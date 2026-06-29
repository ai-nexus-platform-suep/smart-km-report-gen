"""API 请求/响应模型 (人员 B 独占)"""

from datetime import datetime
from typing import Generic, TypeVar

from pydantic import BaseModel, Field

T = TypeVar("T")


class ApiResponse(BaseModel, Generic[T]):
    code: int = 0
    message: str = "success"
    data: T | None = None


class PageResult(BaseModel, Generic[T]):
    items: list[T]
    total: int
    page: int
    size: int


class CreateConversationReq(BaseModel):
    title: str | None = Field(default=None, max_length=200)


class UpdateConversationReq(BaseModel):
    title: str = Field(min_length=1, max_length=200)


class ConversationVO(BaseModel):
    session_id: int
    title: str
    message_count: int
    last_message_at: datetime
    created_at: datetime


class MessageVO(BaseModel):
    message_id: int
    seq: int
    role: str
    content: str
    intent_type: str | None = None
    thinking_steps: str | None = None
    citations: str | None = None
    generate_status: int
    token_usage: int | None = None
    created_at: datetime
    updated_at: datetime


class ConversationDetailVO(BaseModel):
    session_id: int
    title: str
    messages: list[MessageVO]
    total: int


class ChatReq(BaseModel):
    conversation_id: int
    question: str = Field(min_length=1)
    user_id: int | None = None
    selected_kb_ids: list[int] = Field(default_factory=list)
