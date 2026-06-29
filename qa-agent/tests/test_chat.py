"""qa-agent 上下文、思考过程、引用与 SSE 契约测试。

运行: python -m pytest qa-agent/tests/test_chat.py -v
"""

import json
import importlib
import sys
from types import ModuleType, SimpleNamespace

import pytest
from qa_agent.graph.context import (
    build_context,
    truncate_by_tokens,
    estimate_tokens,
    count_context_tokens,
)
from qa_agent.service.thinking_service import (
    add_thinking_step,
    build_thinking_summary,
    to_sse_event,
    steps_total_time,
)
from qa_agent.service.citation_service import (
    build_citations,
    extract_citations,
    insert_citation_marks,
    merge_consecutive_citations,
    citation_to_sse,
)
from qa_agent.core.constants import SSEEventType, ThinkingStepType
from qa_agent.graph.nodes import _build_messages


# ============================================================
# 上下文管理测试
# ============================================================

class TestContext:
    """context.py 单元测试"""

    def test_estimate_tokens_chinese(self):
        """中文字符估算 ~1.5 token/字"""
        text = "你好世界"
        tokens = estimate_tokens(text)
        assert tokens >= 4 * 1.0  # 至少 4 token

    def test_estimate_tokens_english(self):
        """英文单词估算 ~1 token/词"""
        text = "hello world"
        tokens = estimate_tokens(text)
        assert tokens >= 2  # "hello" + "world" = 2 tokens

    def test_estimate_tokens_empty(self):
        assert estimate_tokens("") == 0
        assert estimate_tokens(None) == 0

    def test_build_context_basic(self):
        """基本拼装：user + assistant 交替"""
        messages = [
            {"role": "user", "content": "变压器油温异常怎么处理"},
            {"role": "assistant", "content": "建议检查冷却系统"},
            {"role": "user", "content": "具体步骤呢"},
        ]
        ctx = build_context(messages, max_tokens=8192)
        assert "用户: 变压器油温异常怎么处理" in ctx
        assert "助手: 建议检查冷却系统" in ctx
        assert "用户: 具体步骤呢" in ctx

    def test_build_context_with_system(self):
        """system 消息不参与截断，但拼装到 prompt 里"""
        messages = [
            {"role": "system", "content": "你是电力助手"},
            {"role": "user", "content": "你好"},
        ]
        ctx = build_context(messages, system_prompt="你是电力助手")
        assert "用户: 你好" in ctx

    def test_truncate_by_tokens_keeps_recent(self):
        """token 超限时保留最新消息"""
        messages = [
            {"role": "user", "content": "第一轮问题" * 500},    # 很多 token
            {"role": "user", "content": "最新问题"},
        ]
        result = truncate_by_tokens(messages, max_tokens=100)
        # 最新问题应该留下，第一轮被丢弃
        assert any("最新问题" in (m.get("content", "") if isinstance(m, dict) else str(m)) for m in result)

    def test_truncate_by_tokens_empty(self):
        assert truncate_by_tokens([], 100) == []
        assert truncate_by_tokens(None, 100) == []

    def test_count_context_tokens(self):
        messages = [
            {"role": "user", "content": "你好"},
            {"role": "assistant", "content": "你好，有什么可以帮您"},
        ]
        total = count_context_tokens(messages)
        assert total > 0


# ============================================================
# 思考过程测试
# ============================================================

class TestThinkingService:
    """thinking_service.py 单元测试"""

    def test_add_single_step(self):
        steps = []
        steps = add_thinking_step(steps, ThinkingStepType.INTENT, "识别意图: KNOWLEDGE_QA")
        assert len(steps) == 1
        assert steps[0]["type"] == "intent"
        assert steps[0]["message"] == "识别意图: KNOWLEDGE_QA"
        assert "timestamp" in steps[0]

    def test_add_multiple_steps(self):
        """模拟完整工作流的思考步骤"""
        steps = []
        steps = add_thinking_step(steps, "intent", "识别意图: KNOWLEDGE_QA", elapsed_ms=15)
        steps = add_thinking_step(steps, "retrieve", "检索到 5 条片段", elapsed_ms=320)
        steps = add_thinking_step(steps, "rerank", "重排序后保留 3 条", elapsed_ms=12)
        steps = add_thinking_step(steps, "generate", "回答生成完成", elapsed_ms=2100)
        assert len(steps) == 4

    def test_immutable_steps(self):
        """add_thinking_step 不修改原列表"""
        original = []
        new_steps = add_thinking_step(original, "intent", "test")
        assert len(original) == 0
        assert len(new_steps) == 1

    def test_build_summary(self):
        steps = [
            {"type": "intent", "message": "识别意图: KNOWLEDGE_QA", "timestamp": 1.0},
            {"type": "generate", "message": "完成", "timestamp": 2.0, "elapsed_ms": 1200},
        ]
        summary = build_thinking_summary(steps)
        assert "意图识别" in summary
        assert "1200ms" in summary

    def test_build_summary_empty(self):
        assert build_thinking_summary([]) == ""

    def test_to_sse_event(self):
        step = {"type": "retrieve", "message": "检索到 5 条", "elapsed_ms": 320}
        event = to_sse_event(step)
        assert event["type"] == "thinking"
        assert event["step_type"] == "retrieve"
        assert event["elapsed_ms"] == 320

    def test_steps_total_time(self):
        steps = [
            {"type": "a", "elapsed_ms": 100},
            {"type": "b", "elapsed_ms": 200},
            {"type": "c"},  # 无耗时
        ]
        assert steps_total_time(steps) == 300


# ============================================================
# 引用溯源测试
# ============================================================

class TestCitationService:
    """citation_service.py 单元测试"""

    def test_build_citations_basic(self):
        docs = [
            {"doc_id": "doc_001", "doc_name": "技术监督管理办法.pdf", "snippet": "技术监督是指...", "score": 0.92},
            {"doc_id": "doc_002", "doc_name": "油温异常处理规程.pdf", "snippet": "发现油温异常时...", "score": 0.85},
        ]
        citations = build_citations(docs)
        assert len(citations) == 2
        assert citations[0]["index"] == 1
        assert citations[1]["index"] == 2
        assert citations[0]["doc_name"] == "技术监督管理办法.pdf"

    def test_build_citations_snippet_truncation(self):
        """超长 snippet 截断到 300 字"""
        docs = [{"doc_id": "d1", "doc_name": "test.pdf", "snippet": "A" * 500, "score": 0.9}]
        citations = build_citations(docs)
        assert len(citations[0]["snippet"]) <= 303  # 300 + "..."
        assert citations[0]["full_snippet"] == "A" * 500

    def test_extract_citations_from_text(self):
        """从回答文本提取引用标记 [1] [2,3]"""
        text = "根据规程[1]的要求，以及[2,3]的建议..."
        results = extract_citations(text)
        assert len(results) >= 2

    def test_extract_citations_none(self):
        """没有引用标记的文本返回空"""
        assert extract_citations("这是普通回答，没有引用") == []

    def test_insert_citation_marks(self):
        text = "根据规程，应定期检查油温。"
        citations = [
            {"index": 1, "doc_name": "规程A.pdf", "snippet": "第3条规定..."},
        ]
        result = insert_citation_marks(text, citations)
        assert "引用来源" in result
        assert "规程A.pdf" in result

    def test_merge_consecutive_same_doc(self):
        """连续同一文档的引用合并为一个"""
        citations = [
            {"doc_id": "d1", "index": 1, "doc_name": "A.pdf", "score": 0.9},
            {"doc_id": "d1", "index": 2, "doc_name": "A.pdf", "score": 0.8},
            {"doc_id": "d2", "index": 3, "doc_name": "B.pdf", "score": 0.7},
        ]
        merged = merge_consecutive_citations(citations)
        assert len(merged) == 2  # d1 两次合并为一条 + d2 一条
        assert merged[0]["indices"] == [1, 2]
        assert merged[1]["index"] == 3

    def test_merge_consecutive_different_docs(self):
        """不同文档的引用不合并"""
        citations = [
            {"doc_id": "d1", "index": 1, "score": 0.9},
            {"doc_id": "d2", "index": 2, "score": 0.8},
            {"doc_id": "d1", "index": 3, "score": 0.7},  # 不连续
        ]
        merged = merge_consecutive_citations(citations)
        assert len(merged) == 3  # 全保留

    def test_citation_to_sse(self):
        merged = [{"doc_id": "d1", "indices": [1, 2], "doc_name": "A.pdf"}]
        event = citation_to_sse(merged)
        assert event["type"] == "citation"
        assert event["merged"] is True
        assert len(event["citations"]) == 1


# ============================================================
# 常量测试
# ============================================================

class TestConstants:
    """core/constants.py 验证"""

    def test_sse_event_types(self):
        assert SSEEventType.THINKING == "thinking"
        assert SSEEventType.CITATION == "citation"
        assert SSEEventType.DONE == "done"

    def test_thinking_step_types(self):
        assert ThinkingStepType.INTENT == "intent"
        assert ThinkingStepType.RETRIEVE == "retrieve"
        assert ThinkingStepType.RERANK == "rerank"
        assert ThinkingStepType.GENERATE == "generate"


class TestABCIntegrationContracts:
    """A/B/C 合并后的跨层契约测试。"""

    def test_build_messages_uses_history_without_repeating_current_question(self):
        messages = [
            {"role": "user", "content": "变压器油温异常怎么处理"},
            {"role": "assistant", "content": "先检查冷却系统。"},
            {"role": "user", "content": "具体步骤是什么"},
        ]

        llm_messages = _build_messages(
            "具体步骤是什么",
            documents=[],
            no_knowledge=False,
            history_messages=messages,
        )
        prompt = llm_messages[-1]["content"]

        assert "历史对话上下文" in prompt
        assert "用户: 变压器油温异常怎么处理" in prompt
        assert "助手: 先检查冷却系统。" in prompt
        assert prompt.count("具体步骤是什么") == 1

    def test_build_messages_keeps_documents_after_history(self):
        llm_messages = _build_messages(
            "什么是技术监督？",
            documents=[
                {
                    "doc_id": "doc_1",
                    "doc_name": "技术监督办法.pdf",
                    "snippet": "技术监督是指...",
                    "score": 0.92,
                }
            ],
            no_knowledge=False,
            history_messages=[{"role": "user", "content": "你好"}],
        )
        prompt = llm_messages[-1]["content"]

        assert "历史对话上下文" in prompt
        assert "知识库片段" in prompt
        assert "技术监督办法.pdf" in prompt
        assert "用户问题:什么是技术监督？" in prompt

    def test_thinking_and_citation_sse_payloads_are_canonical(self):
        step_payload = to_sse_event(add_thinking_step([], "retrieve", "检索到 2 条片段")[0])
        citation_payload = citation_to_sse(
            merge_consecutive_citations(
                build_citations(
                    [
                        {"doc_id": "d1", "doc_name": "A.pdf", "snippet": "片段1", "score": 0.9},
                        {"doc_id": "d1", "doc_name": "A.pdf", "snippet": "片段2", "score": 0.8},
                    ]
                )
            )
        )

        assert step_payload["type"] == "thinking"
        assert step_payload["step_type"] == "retrieve"
        assert "event_type" not in step_payload
        assert citation_payload["type"] == "citation"
        assert citation_payload["merged"] is True
        assert citation_payload["citations"][0]["indices"] == [1, 2]

    def test_stream_chat_emits_canonical_thinking_and_citation_events(self, monkeypatch):
        updated_messages = []
        fake_session_module = ModuleType("qa_agent.db.session")
        fake_session_module.get_engine = lambda: object()
        fake_session_module.get_session_factory = lambda: object()

        async def fake_get_db():
            yield SimpleNamespace()

        fake_session_module.get_db = fake_get_db
        monkeypatch.setitem(sys.modules, "qa_agent.db.session", fake_session_module)
        chat_module = importlib.import_module("qa_agent.api.chat")

        async def fake_get_conversation(db, conversation_id):
            return SimpleNamespace(id=conversation_id)

        async def fake_save_message(db, conversation_id, role, content, **kwargs):
            if role == "assistant":
                return SimpleNamespace(id=9002)
            return SimpleNamespace(id=9001)

        async def fake_get_messages(db, conversation_id, page=1, size=200):
            return [SimpleNamespace(role="user", content="什么是技术监督？")], 1

        async def fake_update_message(db, message_id, **kwargs):
            updated_messages.append({"message_id": message_id, **kwargs})
            return SimpleNamespace(id=message_id)

        class FakeAgentGraph:
            async def astream(self, agent_input, stream_mode):
                del agent_input, stream_mode
                first_step = add_thinking_step([], "intent", "识别意图: KNOWLEDGE_QA")[0]
                second_step = add_thinking_step([first_step], "generate", "回答生成完成")[1]
                yield "updates", {
                    "intent": {
                        "intent": "KNOWLEDGE_QA",
                        "thinking_steps": [first_step],
                    }
                }
                yield "custom", {"delta": "答"}
                yield "updates", {
                    "generate": {
                        "final_response": "答案",
                        "citations": [{"doc_id": "d1", "indices": [1, 2], "doc_name": "A.pdf"}],
                        "thinking_steps": [first_step, second_step],
                        "error": None,
                    }
                }

        monkeypatch.setattr(chat_module, "get_conversation", fake_get_conversation)
        monkeypatch.setattr(chat_module, "save_message", fake_save_message)
        monkeypatch.setattr(chat_module, "get_messages", fake_get_messages)
        monkeypatch.setattr(chat_module, "update_message", fake_update_message)
        monkeypatch.setattr(chat_module, "agent_graph", FakeAgentGraph())

        async def collect_events():
            req = chat_module.ChatReq(conversation_id=1, question="什么是技术监督？", selected_kb_ids=[])
            return [event async for event in chat_module._stream_chat(req, SimpleNamespace())]

        import asyncio

        events = asyncio.run(collect_events())
        parsed_events = [(event["event"], json.loads(event["data"])) for event in events]

        thinking_payloads = [data for event, data in parsed_events if event == "thinking"]
        citation_payloads = [data for event, data in parsed_events if event == "citation"]
        message_payloads = [data for event, data in parsed_events if event == "message"]

        assert thinking_payloads[0]["type"] == "thinking"
        assert thinking_payloads[0]["step_type"] == "intent"
        assert citation_payloads == [
            {"type": "citation", "citations": [{"doc_id": "d1", "indices": [1, 2], "doc_name": "A.pdf"}], "merged": True}
        ]
        assert message_payloads[0]["delta"] == "答"
        assert message_payloads[-1]["finished"] is True
        assert json.loads(updated_messages[0]["citations"])[0]["indices"] == [1, 2]

    def test_chat_test_invokes_graph_without_database(self, monkeypatch):
        chat_module = importlib.import_module("qa_agent.api.chat")
        captured_inputs = []

        class FakeAgentGraph:
            async def ainvoke(self, agent_input):
                captured_inputs.append(agent_input)
                return {
                    "intent": "KNOWLEDGE_QA",
                    "mode": "rag",
                    "needs_clarification": False,
                    "classification_source": "rule",
                    "retrieved_docs": [{"doc_id": "d1"}, {"doc_id": "d2"}],
                    "thinking_steps": [{"type": "intent", "message": "识别意图", "timestamp": 1.0}],
                    "citations": [{"doc_id": "d1", "index": 1, "doc_name": "A.pdf"}],
                    "final_response": "测试回答",
                }

        monkeypatch.setattr(chat_module, "agent_graph", FakeAgentGraph())

        async def call_endpoint():
            req = chat_module.ChatTestReq(
                question="什么是技术监督？",
                selected_kb_ids=[1, 2],
                user_id=7,
                messages=[{"role": "assistant", "content": "你好"}],
            )
            return await chat_module.chat_test(req)

        import asyncio

        response = asyncio.run(call_endpoint())

        assert captured_inputs == [
            {
                "messages": [
                    {"role": "assistant", "content": "你好"},
                    {"role": "user", "content": "什么是技术监督？"},
                ],
                "question": "什么是技术监督？",
                "user_id": 7,
                "selected_kb_ids": [1, 2],
                "model_config": {
                    "provider": "deepseek",
                    "base_url": "https://api.deepseek.com",
                    "model_name": "deepseek-chat",
                    "api_key": "",
                    "timeout_seconds": 60,
                },
            }
        ]
        assert response.intent == "KNOWLEDGE_QA"
        assert response.mode == "rag"
        assert response.classification_source == "rule"
        assert response.retrieved_docs_count == 2
        assert response.final_response == "测试回答"

    def test_chat_test_keeps_existing_current_question(self):
        chat_module = importlib.import_module("qa_agent.api.chat")
        req = chat_module.ChatTestReq(
            question="继续说明",
            messages=[{"role": "user", "content": "继续说明"}],
        )

        assert chat_module._test_history_messages(req) == [{"role": "user", "content": "继续说明"}]

    def test_chat_sse_returns_error_when_database_session_init_fails(self, monkeypatch):
        chat_module = importlib.import_module("qa_agent.api.chat")

        def raise_missing_driver():
            raise ModuleNotFoundError("No module named 'aiomysql'")

        monkeypatch.setattr(chat_module, "get_session_factory", raise_missing_driver)

        async def collect_events():
            req = chat_module.ChatReq(conversation_id=1, question="什么是技术监督？", selected_kb_ids=[])
            response = await chat_module.chat(req)
            return [event async for event in response.body_iterator]

        import asyncio

        events = asyncio.run(collect_events())
        parsed_events = [(event["event"], json.loads(event["data"])) for event in events]

        assert parsed_events[0][0] == "error"
        assert "数据库会话初始化失败" in parsed_events[0][1]["message"]
        assert parsed_events[1] == ("done", {})
