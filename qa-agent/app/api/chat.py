"""POST /chat SSE 流式接口与 Agent 事件转换。"""

import json
from collections.abc import AsyncIterator

from fastapi import APIRouter, HTTPException
from sqlalchemy.ext.asyncio import AsyncSession
from sse_starlette.sse import EventSourceResponse

from .schemas import ChatReq, ChatTestReq, ChatTestResp
from ..core.constants import SSEEventType
from ..core.deps import require_user_id
from ..core.user_context import get_user_id
from ..db.constants import (
    GENERATE_STATUS_COMPLETED,
    GENERATE_STATUS_FAILED,
    ROLE_ASSISTANT,
    ROLE_USER,
)
from ..db.repository import get_messages, require_conversation_for_user, save_message, update_message
from ..db.session import get_session_factory
from ..graph.workflow import agent_graph
from ..service.citation_service import citation_to_sse
from ..service.thinking_service import to_sse_event
from ..client.java_client import fetch_llm_config

router = APIRouter(tags=["chat"])


def _sse_payload(event_type: SSEEventType, data: dict) -> dict:
    return {"event": event_type.value, "data": json.dumps(data, ensure_ascii=False)}


def _history_messages(messages: list) -> list[dict]:
    return [{"role": message.role, "content": message.content} for message in messages]


def _test_history_messages(req: ChatTestReq) -> list[dict]:
    messages = [message.model_dump() for message in req.messages]
    if not messages or messages[-1].get("role") != "user" or messages[-1].get("content") != req.question:
        messages.append({"role": "user", "content": req.question})
    return messages


async def _stream_chat(
    req: ChatReq,
    db: AsyncSession,
) -> AsyncIterator[dict]:
    user_id = get_user_id()
    try:
        await require_conversation_for_user(db, req.conversation_id, user_id)
    except ValueError:
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
    model_config = await fetch_llm_config(user_id=user_id)
    agent_input = {
        "messages": _history_messages(history),
        "question": req.question,
        "conversation_id": req.conversation_id,
        "user_id": user_id,
        "selected_kb_ids": req.selected_kb_ids,
        "model_config": model_config,
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
                if chunk.get("type") == "thinking":
                    yield _sse_payload(SSEEventType.THINKING, chunk)
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
                    payload = to_sse_event(step) if isinstance(step, dict) else {"message": str(step)}
                    yield _sse_payload(
                        SSEEventType.THINKING,
                        payload,
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

        if citations:
            yield _sse_payload(SSEEventType.CITATION, citation_to_sse(citations))

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
    require_user_id()

    async def event_generator() -> AsyncIterator[dict]:
        try:
            session_factory = get_session_factory()
        except Exception as exc:
            yield _sse_payload(SSEEventType.ERROR, {"message": f"数据库会话初始化失败: {exc}"})
            yield _sse_payload(SSEEventType.DONE, {})
            return

        async with session_factory() as db:
            try:
                async for event in _stream_chat(req, db):
                    yield event
                await db.commit()
            except Exception:
                await db.rollback()
                raise

    return EventSourceResponse(event_generator())


@router.post("/chat/test", response_model=ChatTestResp)
async def chat_test(req: ChatTestReq) -> ChatTestResp:
    if not req.question.strip():
        raise HTTPException(status_code=400, detail="问题不能为空")

    user_id = require_user_id()
    model_config = await fetch_llm_config(user_id=user_id)
    agent_input = {
        "messages": _test_history_messages(req),
        "question": req.question,
        "user_id": user_id,
        "selected_kb_ids": req.selected_kb_ids,
        "model_config": model_config,
    }
    final_state = await agent_graph.ainvoke(agent_input)
    retrieved_docs = final_state.get("retrieved_docs") or []

    return ChatTestResp(
        intent=final_state.get("intent"),
        mode=final_state.get("mode"),
        needs_clarification=bool(final_state.get("needs_clarification", False)),
        classification_source=final_state.get("classification_source"),
        retrieved_docs_count=len(retrieved_docs),
        thinking_steps=final_state.get("thinking_steps") or [],
        citations=final_state.get("citations") or [],
        final_response=final_state.get("final_response") or final_state.get("error") or "",
    )
