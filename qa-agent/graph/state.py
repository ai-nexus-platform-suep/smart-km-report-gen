"""Agent 状态定义 (人员 A 先写定，B/C 只读)"""

from typing import Annotated, Any
from typing_extensions import NotRequired, TypedDict

from langgraph.graph.message import add_messages


class AgentState(TypedDict):
    messages: Annotated[list[Any], add_messages]
    question: str
    user_id: NotRequired[int]
    conversation_id: NotRequired[int]
    intent: NotRequired[str]
    mode: NotRequired[str]
    selected_kb_ids: NotRequired[list[int]]
    retrieved_docs: NotRequired[list[dict]]
    thinking_steps: NotRequired[list[dict]]
    citations: NotRequired[list[dict]]
    final_response: NotRequired[str]
    error: NotRequired[str | None]
