"""LangGraph 各节点实现 (人员 A 独占)"""

import httpx

from qa_agent.client.knowledge_client import search_knowledge
from qa_agent.core.config import settings
from qa_agent.graph.state import AgentState
from qa_agent.model.embedding import embed_query
from qa_agent.model.reranker import rerank


KNOWLEDGE_INTENT = "KNOWLEDGE_QA"
CHAT_INTENT = "CHAT"
DOCUMENT_SEARCH_INTENT = "DOCUMENT_SEARCH"


def _question_from_state(state: AgentState) -> str:
    question = state.get("question") or ""
    if question:
        return question

    messages = state.get("messages") or []
    if not messages:
        return ""

    last_message = messages[-1]
    content = getattr(last_message, "content", None)
    if content is not None:
        return str(content)
    if isinstance(last_message, dict):
        return str(last_message.get("content") or "")
    return ""


def _append_step(state: AgentState, event_type: str, message: str) -> list[dict]:
    steps = list(state.get("thinking_steps") or [])
    steps.append({"event_type": event_type, "message": message})
    return steps


def _classify_intent(question: str) -> str:
    if not question.strip():
        return CHAT_INTENT

    document_keywords = ("检索", "查找文档", "找文档", "有哪些文档")
    if any(keyword in question for keyword in document_keywords):
        return DOCUMENT_SEARCH_INTENT

    chat_keywords = ("你好", "天气", "讲个笑话", "你是谁", "hello", "hi")
    if any(keyword.lower() in question.lower() for keyword in chat_keywords):
        return CHAT_INTENT

    return KNOWLEDGE_INTENT


def _format_documents(documents: list[dict]) -> str:
    formatted: list[str] = []
    for index, document in enumerate(documents, start=1):
        doc_name = document.get("doc_name") or "未知文档"
        snippet = document.get("snippet") or ""
        score = float(document.get("score") or 0.0)
        formatted.append(f"[{index}] {doc_name} (score={score:.3f})\n{snippet}")
    return "\n\n".join(formatted)


async def _call_chat_model(messages: list[dict]) -> str:
    if not settings.llm_api_key:
        return "LLM API key 未配置，当前仅完成 Agent 工作流编排。"

    try:
        async with httpx.AsyncClient(timeout=settings.llm_timeout) as client:
            response = await client.post(
                f"{settings.llm_api_url.rstrip('/')}/chat/completions",
                headers={"Authorization": f"Bearer {settings.llm_api_key}"},
                json={"model": settings.llm_model_name, "messages": messages},
            )
            response.raise_for_status()
            payload = response.json()
    except (httpx.HTTPError, ValueError):
        return "LLM 服务暂不可用，请稍后重试。"

    choices = payload.get("choices") if isinstance(payload, dict) else None
    if not choices:
        return "LLM 服务未返回有效回答。"

    message = choices[0].get("message") if isinstance(choices[0], dict) else None
    content = message.get("content") if isinstance(message, dict) else None
    return str(content or "LLM 服务未返回有效回答。")


def _build_messages(question: str, documents: list[dict], no_knowledge: bool) -> list[dict]:
    system_prompt = (
        "你是电力行业智能问答助手。回答要准确、简洁。"
        "如果提供了知识库片段，优先依据片段回答；如果没有相关片段，要明确说明未找到相关知识库信息。"
    )

    if documents:
        user_prompt = f"知识库片段:\n{_format_documents(documents)}\n\n用户问题:{question}"
    elif no_knowledge:
        user_prompt = f"未找到相关知识库信息。请基于通用能力谨慎回答用户问题，并说明该限制。\n用户问题:{question}"
    else:
        user_prompt = question

    return [
        {"role": "system", "content": system_prompt},
        {"role": "user", "content": user_prompt},
    ]


async def intent_node(state: AgentState) -> dict:
    question = _question_from_state(state)
    intent = _classify_intent(question)
    return {
        "question": question,
        "intent": intent,
        "mode": "direct" if intent == CHAT_INTENT else "rag",
        "retrieved_docs": [],
        "citations": [],
        "thinking_steps": _append_step(state, "intent", f"识别意图: {intent}"),
        "error": None,
    }


async def retrieve_node(state: AgentState) -> dict:
    question = _question_from_state(state)
    embedding = await embed_query(question)
    documents = await search_knowledge(
        query=question,
        selected_kb_ids=state.get("selected_kb_ids") or [],
        top_k=settings.default_top_k,
        similarity_threshold=settings.default_similarity_threshold,
        embedding=embedding or None,
    )
    filtered_documents = [
        document for document in documents
        if float(document.get("score") or 0.0) >= settings.default_similarity_threshold
    ]
    return {
        "retrieved_docs": filtered_documents,
        "thinking_steps": _append_step(state, "retrieve", f"检索到 {len(filtered_documents)} 条相关片段"),
    }


async def rerank_node(state: AgentState) -> dict:
    documents = await rerank(
        _question_from_state(state),
        state.get("retrieved_docs") or [],
        settings.default_top_k,
    )
    return {
        "retrieved_docs": documents,
        "thinking_steps": _append_step(state, "rerank", f"重排序后保留 {len(documents)} 条片段"),
    }


async def generate_node(state: AgentState) -> dict:
    question = _question_from_state(state)
    documents = state.get("retrieved_docs") or []
    no_knowledge = state.get("intent") in (KNOWLEDGE_INTENT, DOCUMENT_SEARCH_INTENT) and not documents
    answer = await _call_chat_model(_build_messages(question, documents, no_knowledge))
    if no_knowledge and "未找到相关知识库信息" not in answer:
        answer = f"未找到相关知识库信息。{answer}"

    citations = [
        {
            "index": index,
            "doc_id": document.get("doc_id"),
            "doc_name": document.get("doc_name"),
            "snippet": document.get("snippet"),
            "score": document.get("score"),
        }
        for index, document in enumerate(documents, start=1)
    ]
    return {
        "final_response": answer,
        "citations": citations,
        "thinking_steps": _append_step(state, "generate", "回答生成完成"),
        "error": None,
    }
