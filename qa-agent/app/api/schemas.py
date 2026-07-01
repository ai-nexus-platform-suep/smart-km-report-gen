"""API 请求/响应模型 (人员 B 独占)"""

from datetime import datetime
from typing import Any, Generic, Literal, TypeVar

from pydantic import BaseModel, Field, field_serializer

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

    @field_serializer("session_id")
    def serialize_session_id(self, value: int) -> str:
        return str(value)


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

    @field_serializer("message_id")
    def serialize_message_id(self, value: int) -> str:
        return str(value)


class ConversationDetailVO(BaseModel):
    session_id: int
    title: str
    messages: list[MessageVO]
    total: int

    @field_serializer("session_id")
    def serialize_session_id(self, value: int) -> str:
        return str(value)


class ChatReq(BaseModel):
    conversation_id: int
    question: str = Field(min_length=1)
    selected_kb_ids: list[int] = Field(default_factory=list)


class ChatHistoryMessage(BaseModel):
    role: Literal["system", "user", "assistant"] = "user"
    content: str = Field(min_length=1)


class ChatTestReq(BaseModel):
    question: str = Field(min_length=1, examples=["什么是电力技术监督？"])
    selected_kb_ids: list[int] = Field(default_factory=list)
    messages: list[ChatHistoryMessage] = Field(default_factory=list)


class ChatTestResp(BaseModel):
    intent: str | None = None
    mode: str | None = None
    needs_clarification: bool = False
    classification_source: str | None = None
    retrieved_docs_count: int = 0
    thinking_steps: list[dict[str, Any]] = Field(default_factory=list)
    citations: list[dict[str, Any]] = Field(default_factory=list)
    final_response: str = ""
