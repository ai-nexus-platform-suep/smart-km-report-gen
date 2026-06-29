"""POST /chat → SSE 流式 (人员 B 独占)"""

import json
from collections.abc import AsyncIterator

from fastapi import APIRouter, HTTPException
from sqlalchemy.ext.asyncio import AsyncSession
from sse_starlette.sse import EventSourceResponse

from .schemas import ChatReq
from ..core.constants import SSEEventType
from ..db.constants import (
    DEFAULT_USER_ID,
    GENERATE_STATUS_COMPLETED,
    GENERATE_STATUS_FAILED,
    ROLE_ASSISTANT,
    ROLE_USER,
)
from ..db.repository import get_conversation, get_messages, save_message, update_message
from ..db.session import async_session_factory
from ..graph.workflow import agent_graph

router = APIRouter(tags=["chat"])


def _sse_payload(event_type: SSEEventType, data: dict) -> dict:
    return {"event": event_type.value, "data": json.dumps(data, ensure_ascii=False)}


def _history_messages(messages: list) -> list[dict]:
    return [{"role": message.role, "content": message.content} for message in messages]


async def _stream_chat(
    req: ChatReq,
    db: AsyncSession,
) -> AsyncIterator[dict]:
    user_id = req.user_id or DEFAULT_USER_ID
    conversation = await get_conversation(db, req.conversation_id)
    if conversation is None:
        yield _sse_payload(SSEEventType.ERROR, {"message": "会话不存在"})
        yield _sse_payload(SSEEventType.DONE, {})
        return

    await save_message(
        db,
        req.conversation_id,
        ROLE_USER,
        req.question,
        user_id=user_id,
    )

    history, _ = await get_messages(db, req.conversation_id, page=1, size=200)
    agent_input = {
        "messages": _history_messages(history),
        "question": req.question,
        "conversation_id": req.conversation_id,
        "user_id": user_id,
        "selected_kb_ids": req.selected_kb_ids,
    }

    assistant_message = await save_message(
        db,
        req.conversation_id,
        ROLE_ASSISTANT,
        "",
        user_id=user_id,
        generate_status=0,
    )

    emitted_steps = 0
    final_state: dict = {}
    accumulated = ""
    streamed_tokens = False

    try:
        async for mode, chunk in agent_graph.astream(
            agent_input,
            stream_mode=["updates", "custom"],
        ):
            if mode == "custom":
                if not isinstance(chunk, dict):
                    continue
                delta = chunk.get("delta") or ""
                if not delta:
                    continue
                streamed_tokens = True
                accumulated += delta
                yield _sse_payload(
                    SSEEventType.MESSAGE,
                    {
                        "delta": delta,
                        "content": accumulated,
                        "message_id": assistant_message.id,
                        "finished": False,
                    },
                )
                continue

            if mode != "updates" or not isinstance(chunk, dict):
                continue

            for _node_name, update in chunk.items():
                if not isinstance(update, dict):
                    continue
                final_state.update(update)

                steps = update.get("thinking_steps") or []
                for step in steps[emitted_steps:]:
                    yield _sse_payload(
                        SSEEventType.THINKING,
                        step if isinstance(step, dict) else {"message": str(step)},
                    )
                emitted_steps = len(steps)

        intent = final_state.get("intent")
        answer = final_state.get("final_response") or accumulated
        citations = final_state.get("citations") or []
        thinking_steps = final_state.get("thinking_steps") or []
        error = final_state.get("error")

        if error:
            yield _sse_payload(SSEEventType.ERROR, {"message": error})
            await update_message(
                db,
                assistant_message.id,
                content=error,
                generate_status=GENERATE_STATUS_FAILED,
            )
            yield _sse_payload(SSEEventType.DONE, {})
            return

        for citation in citations:
            yield _sse_payload(SSEEventType.CITATION, citation)

        if not streamed_tokens and answer:
            yield _sse_payload(
                SSEEventType.MESSAGE,
                {
                    "delta": answer,
                    "content": answer,
                    "message_id": assistant_message.id,
                    "intent": intent,
                    "finished": False,
                },
            )

        yield _sse_payload(
            SSEEventType.MESSAGE,
            {
                "delta": "",
                "content": answer,
                "message_id": assistant_message.id,
                "intent": intent,
                "finished": True,
            },
        )

        await update_message(
            db,
            assistant_message.id,
            content=answer,
            intent_type=intent,
            thinking_steps=json.dumps(thinking_steps, ensure_ascii=False),
            citations=json.dumps(citations, ensure_ascii=False),
            generate_status=GENERATE_STATUS_COMPLETED,
        )
        yield _sse_payload(
            SSEEventType.DONE,
            {"message_id": assistant_message.id, "conversation_id": req.conversation_id},
        )
    except Exception as exc:
        await update_message(
            db,
            assistant_message.id,
            content=str(exc),
            generate_status=GENERATE_STATUS_FAILED,
        )
        yield _sse_payload(SSEEventType.ERROR, {"message": str(exc)})
        yield _sse_payload(SSEEventType.DONE, {})


@router.post("/chat")
async def chat(req: ChatReq) -> EventSourceResponse:
    if not req.question.strip():
        raise HTTPException(status_code=400, detail="问题不能为空")

    async def event_generator() -> AsyncIterator[dict]:
        async with async_session_factory() as db:
            try:
                async for event in _stream_chat(req, db):
                    yield event
                await db.commit()
            except Exception:
                await db.rollback()
                raise

    return EventSourceResponse(event_generator())
