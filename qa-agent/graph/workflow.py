"""StateGraph 组装 + 条件路由 (人员 A 独占)"""

from typing import Literal

from langgraph.graph import END, START, StateGraph

from qa_agent.graph.state import AgentState
from qa_agent.graph.nodes import CHAT_INTENT, intent_node, retrieve_node, rerank_node, generate_node


def build_workflow() -> StateGraph:
    workflow = StateGraph(AgentState)

    workflow.add_node("intent", intent_node)
    workflow.add_node("retrieve", retrieve_node)
    workflow.add_node("rerank", rerank_node)
    workflow.add_node("generate", generate_node)

    workflow.add_edge(START, "intent")
    workflow.add_conditional_edges("intent", route_by_intent, {
        "rag": "retrieve",
        "direct": "generate",
    })
    workflow.add_conditional_edges("retrieve", route_if_empty, {
        "rerank": "rerank",
        "generate": "generate",
    })
    workflow.add_edge("rerank", "generate")
    workflow.add_edge("generate", END)

    return workflow


def route_by_intent(state: AgentState) -> Literal["rag", "direct"]:
    if state.get("intent") == CHAT_INTENT:
        return "direct"
    return "rag"


def route_if_empty(state: AgentState) -> Literal["rerank", "generate"]:
    if state.get("retrieved_docs"):
        return "rerank"
    return "generate"


agent_graph = build_workflow().compile()
