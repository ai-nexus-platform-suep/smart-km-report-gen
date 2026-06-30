"""对话/消息 CRUD 方法 (人员 B 独占)"""

from datetime import datetime

from sqlalchemy import func, select, update
from sqlalchemy.ext.asyncio import AsyncSession

from qa_agent.db.constants import (
    DEFAULT_TITLE,
    DEFAULT_USER_ID,
    GENERATE_STATUS_COMPLETED,
    ROLE_USER,
    STATUS_ACTIVE,
    STATUS_DELETED,
    TITLE_MAX_LENGTH,
)
from qa_agent.db.id_gen import id_generator
from qa_agent.db.models import QaMessage, QaSession


def _build_title_from_content(content: str) -> str:
    trimmed = content.strip()
    if len(trimmed) <= TITLE_MAX_LENGTH:
        return trimmed
    return trimmed[:TITLE_MAX_LENGTH] + "..."


async def create_conversation(
    session: AsyncSession,
    user_id: int = DEFAULT_USER_ID,
    title: str | None = None,
) -> QaSession:
    now = datetime.now()
    resolved_title = (title or DEFAULT_TITLE).strip() or DEFAULT_TITLE
    conversation = QaSession(
        id=id_generator.next_id(),
        user_id=user_id,
        title=resolved_title,
        status=STATUS_ACTIVE,
        message_count=0,
        last_message_at=now,
        created_at=now,
        updated_at=now,
    )
    session.add(conversation)
    await session.flush()
    return conversation


async def list_conversations(
    session: AsyncSession,
    user_id: int = DEFAULT_USER_ID,
    page: int = 1,
    size: int = 20,
) -> tuple[list[QaSession], int]:
    page = max(page, 1)
    size = max(min(size, 100), 1)
    offset = (page - 1) * size

    count_stmt = select(func.count()).select_from(QaSession).where(
        QaSession.user_id == user_id,
        QaSession.status == STATUS_ACTIVE,
    )
    total = (await session.execute(count_stmt)).scalar_one()

    stmt = (
        select(QaSession)
        .where(QaSession.user_id == user_id, QaSession.status == STATUS_ACTIVE)
        .order_by(QaSession.last_message_at.desc(), QaSession.id.desc())
        .offset(offset)
        .limit(size)
    )
    items = list((await session.execute(stmt)).scalars().all())
    return items, total


async def delete_conversation(session: AsyncSession, conversation_id: int) -> None:
    conversation = await get_conversation(session, conversation_id)
    if conversation is None:
        raise ValueError("会话不存在")

    now = datetime.now()
    await session.execute(
        update(QaSession)
        .where(QaSession.id == conversation_id, QaSession.status == STATUS_ACTIVE)
        .values(status=STATUS_DELETED, deleted_at=now, updated_at=now)
    )
    await session.execute(
        update(QaMessage)
        .where(QaMessage.session_id == conversation_id, QaMessage.status == STATUS_ACTIVE)
        .values(status=STATUS_DELETED, deleted_at=now, updated_at=now)
    )


async def get_conversation(session: AsyncSession, conversation_id: int) -> QaSession | None:
    stmt = select(QaSession).where(
        QaSession.id == conversation_id,
        QaSession.status == STATUS_ACTIVE,
    )
    return (await session.execute(stmt)).scalar_one_or_none()


async def update_conversation_title(
    session: AsyncSession,
    conversation_id: int,
    title: str,
) -> QaSession:
    conversation = await get_conversation(session, conversation_id)
    if conversation is None:
        raise ValueError("会话不存在")

    resolved_title = title.strip() or DEFAULT_TITLE
    conversation.title = resolved_title
    conversation.updated_at = datetime.now()
    await session.flush()
    return conversation


async def _touch_session_after_message(
    session: AsyncSession,
    conversation: QaSession,
    content: str,
    role: str,
) -> None:
    now = datetime.now()
    conversation.message_count += 1
    conversation.last_message_at = now
    conversation.updated_at = now

    if (
        role == ROLE_USER
        and conversation.title == DEFAULT_TITLE
        and content.strip()
    ):
        conversation.title = _build_title_from_content(content)

    await session.flush()


async def save_message(
    session: AsyncSession,
    conversation_id: int,
    role: str,
    content: str,
    *,
    user_id: int = DEFAULT_USER_ID,
    intent_type: str | None = None,
    thinking_steps: str | None = None,
    citations: str | None = None,
    generate_status: int = GENERATE_STATUS_COMPLETED,
    token_usage: int | None = None,
) -> QaMessage:
    conversation = await get_conversation(session, conversation_id)
    if conversation is None:
        raise ValueError("会话不存在")

    now = datetime.now()
    next_seq = conversation.message_count + 1
    message = QaMessage(
        id=id_generator.next_id(),
        session_id=conversation_id,
        user_id=user_id,
        seq=next_seq,
        role=role,
        content=content,
        intent_type=intent_type,
        thinking_steps=thinking_steps,
        citations=citations,
        generate_status=generate_status,
        token_usage=token_usage,
        status=STATUS_ACTIVE,
        created_at=now,
        updated_at=now,
    )
    session.add(message)
    await _touch_session_after_message(session, conversation, content, role)
    await session.flush()
    return message


async def get_messages(
    session: AsyncSession,
    conversation_id: int,
    page: int = 1,
    size: int = 50,
) -> tuple[list[QaMessage], int]:
    conversation = await get_conversation(session, conversation_id)
    if conversation is None:
        raise ValueError("会话不存在")

    page = max(page, 1)
    size = max(min(size, 200), 1)
    offset = (page - 1) * size

    count_stmt = select(func.count()).select_from(QaMessage).where(
        QaMessage.session_id == conversation_id,
        QaMessage.status == STATUS_ACTIVE,
    )
    total = (await session.execute(count_stmt)).scalar_one()

    stmt = (
        select(QaMessage)
        .where(QaMessage.session_id == conversation_id, QaMessage.status == STATUS_ACTIVE)
        .order_by(QaMessage.seq.asc())
        .offset(offset)
        .limit(size)
    )
    items = list((await session.execute(stmt)).scalars().all())
    return items, total


async def update_message(
    session: AsyncSession,
    message_id: int,
    *,
    content: str | None = None,
    intent_type: str | None = None,
    thinking_steps: str | None = None,
    citations: str | None = None,
    generate_status: int | None = None,
    token_usage: int | None = None,
) -> QaMessage:
    stmt = select(QaMessage).where(QaMessage.id == message_id, QaMessage.status == STATUS_ACTIVE)
    message = (await session.execute(stmt)).scalar_one_or_none()
    if message is None:
        raise ValueError("消息不存在")

    if content is not None:
        message.content = content
    if intent_type is not None:
        message.intent_type = intent_type
    if thinking_steps is not None:
        message.thinking_steps = thinking_steps
    if citations is not None:
        message.citations = citations
    if generate_status is not None:
        message.generate_status = generate_status
    if token_usage is not None:
        message.token_usage = token_usage
    message.updated_at = datetime.now()
    await session.flush()
    return message

