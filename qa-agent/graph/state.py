"""Agent 状态定义 (人员 A 先写定，B/C 只读)"""

from typing import Annotated, Any, Sequence
from typing_extensions import TypedDict

from langgraph.graph.message import add_messages


class AgentState(TypedDict):
    messages: Annotated[Sequence[Any], add_messages]
    question: str
    user_id: int
    conversation_id: int
    intent: str
    mode: str
    selected_kb_ids: list[int]
    retrieved_docs: list[dict]
    thinking_steps: list[dict]
    citations: list[dict]
    final_response: str
    error: str | None
