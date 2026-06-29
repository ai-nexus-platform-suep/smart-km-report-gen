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
    intent_confidence: NotRequired[float]
    route_reason: NotRequired[str]
    classification_source: NotRequired[str]
    needs_clarification: NotRequired[bool]
    mode: NotRequired[str]
    selected_kb_ids: NotRequired[list[int]]
    retrieved_docs: NotRequired[list[dict]]
    thinking_steps: NotRequired[list[dict]]
    citations: NotRequired[list[dict]]
    final_response: NotRequired[str]
    model_config: NotRequired[dict]
    error: NotRequired[str | None]
