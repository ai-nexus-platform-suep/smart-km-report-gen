"""LangGraph 节点实现与共享上下文/引用集成。"""

import json
import time
from collections.abc import AsyncIterator

import httpx
from langgraph.config import get_stream_writer

from app.client.knowledge_client import search_knowledge
from app.core.config import settings
from app.core.logging import get_logger
from app.graph.context import build_context
from app.graph.state import AgentState
from app.model.embedding import embed_query
from app.model.reranker import rerank
from app.service.citation_service import build_citations, merge_consecutive_citations
from app.service.thinking_service import add_thinking_step, to_sse_event

logger = get_logger("graph")


def _get_llm_config(state: dict) -> dict:
    """从 state 中获取 LLM 配置，优先用 Java 传入的 model_config，fallback 到本地 settings"""
    mc = state.get("model_config") if isinstance(state, dict) else {}
    if mc and mc.get("api_key") and mc.get("base_url"):
        return mc
    return {
        "provider": "deepseek",
        "base_url": settings.llm_api_url,
        "model_name": settings.llm_model_name,
        "api_key": settings.llm_api_key,
        "timeout_seconds": settings.llm_timeout,
    }


KNOWLEDGE_INTENT = "KNOWLEDGE_QA"
CHAT_INTENT = "CHAT"
DOCUMENT_SEARCH_INTENT = "DOCUMENT_SEARCH"
REPORT_GENERATION_INTENT = "REPORT_GENERATION"
KB_MANAGEMENT_INTENT = "KB_MANAGEMENT"
TASK_ACTION_INTENT = "TASK_ACTION"

INTENT_CONFIDENCE_THRESHOLD = 0.6
RAG_INTENTS = (KNOWLEDGE_INTENT, DOCUMENT_SEARCH_INTENT)
UNSUPPORTED_ACTION_INTENTS = (REPORT_GENERATION_INTENT, KB_MANAGEMENT_INTENT, TASK_ACTION_INTENT)
KNOWN_INTENTS = RAG_INTENTS + (CHAT_INTENT,) + UNSUPPORTED_ACTION_INTENTS


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
    return add_thinking_step(list(state.get("thinking_steps") or []), event_type, message)


def _emit_start_step(event_type: str, message: str) -> None:
    try:
        writer = get_stream_writer()
    except RuntimeError:
        return

    writer(to_sse_event({"type": event_type, "message": message, "phase": "start"}))


def _intent_decision(
    intent: str,
    confidence: float,
    reason: str,
    needs_clarification: bool,
    source: str,
) -> dict:
    return {
        "intent": intent,
        "confidence": max(0.0, min(1.0, confidence)),
        "reason": reason,
        "needs_clarification": needs_clarification,
        "source": source,
    }


def _classify_intent_by_rule(question: str) -> dict | None:
    clean_question = question.strip()
    lower_question = clean_question.lower()
    if not clean_question:
        return _intent_decision(CHAT_INTENT, 0.4, "用户输入为空，需要补充问题。", True, "rule")

    chat_keywords = ("你好", "天气", "讲个笑话", "你是谁", "hello", "hi")
    if any(keyword.lower() in lower_question for keyword in chat_keywords):
        return _intent_decision(CHAT_INTENT, 0.95, "命中闲聊关键词。", False, "rule")

    report_keywords = ("生成报告", "写报告", "报告生成", "导出报告", "周报", "日报", "月报")
    report_actions = ("生成", "写", "导出", "整理")
    if any(keyword in clean_question for keyword in report_keywords) or (
        "报告" in clean_question and any(action in clean_question for action in report_actions)
    ):
        return _intent_decision(REPORT_GENERATION_INTENT, 0.9, "识别为报告生成请求，但当前迭代未执行该能力。", True, "rule")

    kb_keywords = ("创建知识库", "删除知识库", "新增知识库", "知识库管理", "上传文档", "导入文档", "删除文档")
    if any(keyword in clean_question for keyword in kb_keywords):
        return _intent_decision(KB_MANAGEMENT_INTENT, 0.9, "识别为知识库管理请求，但当前迭代未执行该能力。", True, "rule")

    action_keywords = ("创建任务", "安排任务", "执行任务", "提醒我", "帮我执行", "提交审批")
    if any(keyword in clean_question for keyword in action_keywords):
        return _intent_decision(TASK_ACTION_INTENT, 0.85, "识别为任务动作请求，但当前迭代未执行该能力。", True, "rule")

    document_keywords = ("检索", "查找文档", "找文档", "有哪些文档")
    if any(keyword in clean_question for keyword in document_keywords):
        return _intent_decision(DOCUMENT_SEARCH_INTENT, 0.95, "命中文档检索关键词。", False, "rule")

    ambiguous_keywords = ("帮我分析一下", "这份材料怎么样", "帮我看看", "分析一下", "评价一下", "处理一下")
    if any(keyword in clean_question for keyword in ambiguous_keywords):
        return _intent_decision(KNOWLEDGE_INTENT, 0.35, "请求缺少材料、目标或输出要求，需要先澄清。", True, "rule")

    knowledge_keywords = ("什么是", "如何", "为什么", "请解释", "介绍一下", "技术监督", "规定", "流程", "办法")
    if any(keyword in clean_question for keyword in knowledge_keywords):
        return _intent_decision(KNOWLEDGE_INTENT, 0.8, "命中知识问答表达。", False, "rule")

    return None


def _classification_messages(question: str) -> list[dict]:
    return [
        {
            "role": "system",
            "content": (
                "你是智能问答系统的意图分类器。只返回 JSON，不要输出 Markdown。"
                "可选 intent: CHAT, KNOWLEDGE_QA, DOCUMENT_SEARCH, REPORT_GENERATION, KB_MANAGEMENT, TASK_ACTION。"
                "返回字段: intent, confidence, reason, needs_clarification。"
            ),
        },
        {"role": "user", "content": question},
    ]


def _extract_json_object(content: str) -> dict | None:
    start = content.find("{")
    end = content.rfind("}")
    if start < 0 or end < start:
        return None
    try:
        payload = json.loads(content[start:end + 1])
    except (TypeError, ValueError):
        return None
    return payload if isinstance(payload, dict) else None


def _as_bool(value: object) -> bool:
    if isinstance(value, bool):
        return value
    if isinstance(value, str):
        return value.strip().lower() in ("true", "1", "yes", "y")
    return bool(value)


def _normalize_llm_decision(payload: dict) -> dict | None:
    intent = str(payload.get("intent") or "").upper()
    if intent not in KNOWN_INTENTS:
        return None

    try:
        confidence = float(payload.get("confidence", 0.0))
    except (TypeError, ValueError):
        confidence = 0.0

    reason = str(payload.get("reason") or "LLM 返回结构化分类结果。")
    needs_clarification = _as_bool(payload.get("needs_clarification"))
    if confidence < INTENT_CONFIDENCE_THRESHOLD or intent in UNSUPPORTED_ACTION_INTENTS:
        needs_clarification = True
    return _intent_decision(intent, confidence, reason, needs_clarification, "llm")


async def _classify_intent_with_llm(question: str, config: dict) -> dict | None:
    if not config.get("api_key"):
        return None
    base_url = config["base_url"].rstrip("/")
    timeout = config.get("timeout_seconds", settings.llm_timeout)
    try:
        async with httpx.AsyncClient(timeout=timeout) as client:
            response = await client.post(
                f"{base_url}/chat/completions",
                headers={"Authorization": f"Bearer {config['api_key']}"},
                json={"model": config.get("model_name", "deepseek-chat"), "messages": _classification_messages(question)},
            )
            response.raise_for_status()
            payload = response.json()
    except (httpx.HTTPError, ValueError):
        return None

    choices = payload.get("choices") if isinstance(payload, dict) else None
    if not choices:
        return None

    message = choices[0].get("message") if isinstance(choices[0], dict) else None
    content = message.get("content") if isinstance(message, dict) else None
    if content is None:
        return None
    json_payload = _extract_json_object(str(content))
    return _normalize_llm_decision(json_payload) if json_payload else None


async def _classify_intent(question: str, config: dict) -> dict:
    rule_decision = _classify_intent_by_rule(question)
    if rule_decision is not None:
        return rule_decision

    llm_decision = await _classify_intent_with_llm(question, config)
    if llm_decision is not None:
        return llm_decision

    return _intent_decision(KNOWLEDGE_INTENT, 0.3, "未能可靠识别用户意图，需要补充上下文。", True, "fallback")


def _mode_from_decision(decision: dict) -> str:
    if decision.get("needs_clarification"):
        return "clarify"
    if decision.get("intent") == CHAT_INTENT:
        return "direct"
    if decision.get("intent") in RAG_INTENTS:
        return "rag"
    return "clarify"


def _format_documents(documents: list[dict]) -> str:
    formatted: list[str] = []
    for index, document in enumerate(documents, start=1):
        doc_name = document.get("doc_name") or "未知文档"
        snippet = document.get("snippet") or ""
        score = float(document.get("score") or 0.0)
        formatted.append(f"[{index}] {doc_name} (score={score:.3f})\n{snippet}")
    return "\n\n".join(formatted)


def _message_role_content(message: object) -> tuple[str, str]:
    if isinstance(message, dict):
        role = str(message.get("role") or "user")
        content = str(message.get("content") or "")
    else:
        role = str(getattr(message, "role", None) or getattr(message, "type", None) or "user")
        content = str(getattr(message, "content", "") or "")

    role = {"human": "user", "ai": "assistant"}.get(role, role)
    return role, content


def _normalize_history_messages(messages: list) -> list[dict]:
    normalized: list[dict] = []
    for message in messages or []:
        role, content = _message_role_content(message)
        normalized.append({"role": role, "content": content})
    return normalized


def _history_without_current_question(messages: list, question: str) -> list:
    history = _normalize_history_messages(messages)
    if not history:
        return history

    last_message = history[-1]
    role = last_message.get("role")
    content = last_message.get("content")

    if role == "user" and str(content or "") == question:
        return history[:-1]
    return history


async def _stream_chat_model(messages: list[dict], config: dict) -> AsyncIterator[str]:
    """流式调用 LLM，逐 token yield 文本片段。"""
    if not config.get("api_key"):
        yield "LLM API key 未配置，当前仅完成 Agent 工作流编排。"
        return

    base_url = config["base_url"].rstrip("/")
    timeout = config.get("timeout_seconds", settings.llm_timeout)
    headers = {"Authorization": f"Bearer {config['api_key']}"}
    body = {
        "model": config.get("model_name", "deepseek-chat"),
        "messages": messages,
        "stream": True,
    }

    try:
        async with httpx.AsyncClient(timeout=timeout) as client:
            async with client.stream("POST", f"{base_url}/chat/completions", headers=headers, json=body) as response:
                response.raise_for_status()
                async for line in response.aiter_lines():
                    if not line or not line.startswith("data:"):
                        continue
                    payload = line[len("data:") :].strip()
                    if payload == "[DONE]":
                        break
                    try:
                        chunk = json.loads(payload)
                    except json.JSONDecodeError:
                        continue
                    choices = chunk.get("choices") if isinstance(chunk, dict) else None
                    if not choices:
                        continue
                    delta = choices[0].get("delta") if isinstance(choices[0], dict) else None
                    content = delta.get("content") if isinstance(delta, dict) else None
                    if content:
                        yield str(content)
    except httpx.HTTPError:
        yield "LLM 服务暂不可用，请稍后重试。"


async def _call_chat_model(messages: list[dict], config: dict) -> str:
    parts: list[str] = []
    async for token in _stream_chat_model(messages, config):
        parts.append(token)
    return "".join(parts) or "LLM 服务未返回有效回答。"


def _build_messages(
    question: str,
    documents: list[dict],
    no_knowledge: bool,
    history_messages: list | None = None,
) -> list[dict]:
    system_prompt = (
        "你是电力行业智能问答助手。回答要准确、简洁。"
        "如果提供了知识库片段，优先依据片段回答；如果没有相关片段，要明确说明未找到相关知识库信息。"
    )
    context = build_context(
        _history_without_current_question(history_messages or [], question),
        system_prompt=system_prompt,
        current_question=question,
    )
    prompt_parts: list[str] = []

    if context:
        prompt_parts.append(f"历史对话上下文:\n{context}")

    if documents:
        prompt_parts.append(f"知识库片段:\n{_format_documents(documents)}")
    elif no_knowledge:
        prompt_parts.append("未找到相关知识库信息。请基于通用能力谨慎回答用户问题，并说明该限制。")

    prompt_parts.append(f"用户问题:{question}")
    user_prompt = "\n\n".join(prompt_parts)

    return [
        {"role": "system", "content": system_prompt},
        {"role": "user", "content": user_prompt},
    ]


async def intent_node(state: AgentState) -> dict:
    _emit_start_step("intent", "正在识别用户意图")
    question = _question_from_state(state)
    logger.info("意图识别 问题=%s", question[:60])
    config = _get_llm_config(state)
    decision = await _classify_intent(question, config)
    intent = decision["intent"]
    confidence = decision["confidence"]
    reason = decision["reason"]
    source = decision["source"]
    mode = _mode_from_decision(decision)
    return {
        "question": question,
        "intent": intent,
        "intent_confidence": confidence,
        "route_reason": reason,
        "classification_source": source,
        "needs_clarification": decision["needs_clarification"],
        "mode": mode,
        "retrieved_docs": [],
        "citations": [],
        "thinking_steps": _append_step(
            state,
            "intent",
            f"识别意图: {intent}，置信度 {confidence:.2f}，来源 {source}。{reason}",
        ),
        "error": None,
    }


async def clarify_node(state: AgentState) -> dict:
    _emit_start_step("clarify", "正在准备澄清问题")
    intent = state.get("intent") or KNOWLEDGE_INTENT
    reason = state.get("route_reason") or "当前问题缺少必要上下文。"
    if intent in UNSUPPORTED_ACTION_INTENTS:
        answer = f"我已识别到这是 {intent} 类型请求。当前 Agent 迭代只支持问答和文档检索编排，暂不直接执行该操作。请补充目标、材料范围和期望输出，我可以先帮你澄清需求或转成可问答的问题。"
    else:
        answer = f"我还需要更多信息才能准确处理。{reason} 请补充要分析的材料、具体问题或期望输出。"

    return {
        "final_response": answer,
        "retrieved_docs": [],
        "citations": [],
        "thinking_steps": _append_step(state, "clarify", "上下文不足或能力未开放，先向用户澄清。"),
        "error": None,
    }


async def retrieve_node(state: AgentState) -> dict:
    _emit_start_step("retrieve", "正在检索知识库片段")
    question = _question_from_state(state)
    kb_ids = state.get("selected_kb_ids") or []
    logger.info("知识检索 问题=%s kb_ids=%s", question[:60], kb_ids)
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
    _emit_start_step("rerank", "正在重排序候选片段")
    candidate_count = len(state.get("retrieved_docs") or [])
    logger.info("重排序 候选片段=%d", candidate_count)
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
    _emit_start_step("generate", "正在生成回答")
    start = time.perf_counter()
    question = _question_from_state(state)
    model_name = _get_llm_config(state).get("model_name", "unknown")
    logger.info("生成回答 模型=%s 问题=%s", model_name, question[:60])
    documents = state.get("retrieved_docs") or []
    no_knowledge = state.get("intent") in RAG_INTENTS and not documents
    llm_messages = _build_messages(question, documents, no_knowledge, state.get("messages") or [])
    config = _get_llm_config(state)

    try:
        writer = get_stream_writer()
    except RuntimeError:
        writer = None

    parts: list[str] = []
    async for token in _stream_chat_model(llm_messages, config):
        parts.append(token)
        if writer is not None:
            writer({"delta": token})

    answer = "".join(parts) or "LLM 服务未返回有效回答。"
    if no_knowledge and "未找到相关知识库信息" not in answer:
        answer = f"未找到相关知识库信息。{answer}"

    citations = merge_consecutive_citations(build_citations(documents))
    return {
        "final_response": answer,
        "citations": citations,
        "thinking_steps": _append_step(state, "generate", "回答生成完成"),
        "error": None,
    }
