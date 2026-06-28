"""对话/消息 CRUD 方法 (人员 B 独占)"""

from sqlalchemy.ext.asyncio import AsyncSession

from qa_agent.db.models import Conversation, Message


async def create_conversation(session: AsyncSession, user_id: int, title: str = "新对话") -> Conversation:
    pass


async def list_conversations(session: AsyncSession, user_id: int) -> list[Conversation]:
    pass


async def delete_conversation(session: AsyncSession, conversation_id: int) -> None:
    pass


async def get_conversation(session: AsyncSession, conversation_id: int) -> Conversation | None:
    pass


async def update_conversation_title(session: AsyncSession, conversation_id: int, title: str) -> Conversation:
    pass


async def save_message(session: AsyncSession, conversation_id: int, role: str, content: str) -> Message:
    pass


async def get_messages(session: AsyncSession, conversation_id: int) -> list[Message]:
    pass
