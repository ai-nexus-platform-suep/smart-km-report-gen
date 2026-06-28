"""StateGraph 组装 + 条件路由 (人员 A 独占)"""

from langgraph.graph import END, StateGraph

from qa_agent.graph.state import AgentState
from qa_agent.graph.nodes import intent_node, retrieve_node, rerank_node, generate_node


def build_workflow() -> StateGraph:
    workflow = StateGraph(AgentState)

    workflow.add_node("intent", intent_node)
    workflow.add_node("retrieve", retrieve_node)
    workflow.add_node("rerank", rerank_node)
    workflow.add_node("generate", generate_node)

    workflow.set_entry_point("intent")
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


def route_by_intent(state: AgentState) -> str:
    if state.get("intent") in ("文档检索",):
        return "direct"
    return "rag"


def route_if_empty(state: AgentState) -> str:
    if state.get("retrieved_docs"):
        return "rerank"
    return "generate"


agent_graph = build_workflow().compile()
