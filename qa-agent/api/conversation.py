"""GET/POST/DELETE /conversations (人员 B 独占)"""

from fastapi import APIRouter, Depends, HTTPException, Query
from sqlalchemy.ext.asyncio import AsyncSession

from ..api.schemas import (
    ApiResponse,
    ConversationDetailVO,
    ConversationVO,
    CreateConversationReq,
    MessageVO,
    PageResult,
    UpdateConversationReq,
)
from qa_agent.db.constants import DEFAULT_USER_ID
from qa_agent.db.models import QaMessage, QaSession
from qa_agent.db.repository import (
    create_conversation,
    delete_conversation,
    get_conversation,
    get_messages,
    list_conversations,
    update_conversation_title,
)
from qa_agent.db.session import get_db

router = APIRouter(tags=["conversations"])


def _to_conversation_vo(session: QaSession) -> ConversationVO:
    return ConversationVO(
        session_id=session.id,
        title=session.title,
        message_count=session.message_count,
        last_message_at=session.last_message_at,
        created_at=session.created_at,
    )


def _to_message_vo(message: QaMessage) -> MessageVO:
    return MessageVO(
        message_id=message.id,
        seq=message.seq,
        role=message.role,
        content=message.content,
        intent_type=message.intent_type,
        thinking_steps=message.thinking_steps,
        citations=message.citations,
        generate_status=message.generate_status,
        token_usage=message.token_usage,
        created_at=message.created_at,
        updated_at=message.updated_at,
    )


@router.get("/conversations")
async def list_conversations_api(
    page: int = Query(default=1, ge=1),
    size: int = Query(default=20, ge=1, le=100),
    user_id: int = Query(default=DEFAULT_USER_ID),
    db: AsyncSession = Depends(get_db),
) -> ApiResponse[PageResult[ConversationVO]]:
    items, total = await list_conversations(db, user_id=user_id, page=page, size=size)
    return ApiResponse(
        data=PageResult(
            items=[_to_conversation_vo(item) for item in items],
            total=total,
            page=page,
            size=size,
        )
    )


@router.post("/conversations")
async def create_conversation_api(
    req: CreateConversationReq | None = None,
    user_id: int = Query(default=DEFAULT_USER_ID),
    db: AsyncSession = Depends(get_db),
) -> ApiResponse[ConversationVO]:
    title = req.title if req else None
    conversation = await create_conversation(db, user_id=user_id, title=title)
    return ApiResponse(data=_to_conversation_vo(conversation))


@router.delete("/conversations/{conversation_id}")
async def delete_conversation_api(
    conversation_id: int,
    db: AsyncSession = Depends(get_db),
) -> ApiResponse[None]:
    try:
        await delete_conversation(db, conversation_id)
    except ValueError as exc:
        raise HTTPException(status_code=404, detail=str(exc)) from exc
    return ApiResponse(data=None)


@router.get("/conversations/{conversation_id}/messages")
async def get_messages_api(
    conversation_id: int,
    page: int = Query(default=1, ge=1),
    size: int = Query(default=50, ge=1, le=200),
    db: AsyncSession = Depends(get_db),
) -> ApiResponse[ConversationDetailVO]:
    try:
        messages, total = await get_messages(db, conversation_id, page=page, size=size)
        conversation = await get_conversation(db, conversation_id)
    except ValueError as exc:
        raise HTTPException(status_code=404, detail=str(exc)) from exc

    if conversation is None:
        raise HTTPException(status_code=404, detail="会话不存在")

    return ApiResponse(
        data=ConversationDetailVO(
            session_id=conversation.id,
            title=conversation.title,
            messages=[_to_message_vo(message) for message in messages],
            total=total,
        )
    )


@router.patch("/conversations/{conversation_id}")
async def update_conversation_api(
    conversation_id: int,
    req: UpdateConversationReq,
    db: AsyncSession = Depends(get_db),
) -> ApiResponse[ConversationVO]:
    try:
        conversation = await update_conversation_title(db, conversation_id, req.title)
    except ValueError as exc:
        raise HTTPException(status_code=404, detail=str(exc)) from exc
    return ApiResponse(data=_to_conversation_vo(conversation))
