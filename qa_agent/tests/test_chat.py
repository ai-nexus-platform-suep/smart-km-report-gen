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
    _format_message,
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

    def test_estimate_tokens_mixed_cn_en(self):
        """中英文混合文本 token 估算"""
        text = "变压器 oil 温度 abnormal，请检查 cooling system。"
        tokens = estimate_tokens(text)
        # 中文字符 + 英文单词 + 标点都应被计入
        assert tokens > 10

    def test_estimate_tokens_numbers_and_punctuation(self):
        """数字和标点符号被计入 token 估算"""
        text = "第3.2.1条规定：温度应≤95℃，压力≥0.5MPa。"
        tokens = estimate_tokens(text)
        assert tokens > 0
        # 纯中文(基数) vs 含数字符号的同一句话，含数字的应更多
        text_plain = "第条规定：温度应，压力。"
        tokens_plain = estimate_tokens(text_plain)
        assert tokens >= tokens_plain

    def test_build_context_token_overflow_truncation(self):
        """token 超限时触发截断并插入提示信息"""
        # 构造 100 轮对话，每条约 120 中文 ≈ 180 tokens，总量远超 8192
        messages = []
        for i in range(100):
            messages.append(
                {"role": "user", "content": f"第{i}轮问题" + "测试内容" * 30}
            )
            messages.append(
                {"role": "assistant", "content": f"第{i}轮回答" + "回答内容" * 30}
            )

        ctx = build_context(messages, max_tokens=8192)

        # 总量远超上限 → 必须触发截断
        assert "[更早的对话内容已被截断]" in ctx
        # 最新轮次应该保留
        assert "第99轮" in ctx
        # 最旧轮次应该被丢弃
        assert "第0轮问题" not in ctx

    def test_build_context_current_question_reserved(self):
        """current_question 参与固定开销预留，不会被截断"""
        messages = [
            {"role": "user", "content": "旧问题" * 50},
            {"role": "assistant", "content": "旧回答" * 50},
        ]
        long_question = "当前最新问题" * 200  # ~1200 中文字符 ≈ 1800 tokens

        # max_tokens=500，current_question 已占 ~1800 tokens 固定开销
        # available = 500 - 1800 - 200 = -1500 → 历史消息全部丢弃
        ctx = build_context(
            messages, max_tokens=500, current_question=long_question
        )

        assert "[更早的对话内容已被截断]" in ctx
        # 旧消息因 token 超限被丢弃
        assert "旧问题" not in ctx

    def test_build_context_system_prompt_overhead(self):
        """system_prompt 参数计入固定开销"""
        messages = [
            {"role": "user", "content": "简短问题"},
            {"role": "assistant", "content": "简短回答"},
        ]
        long_system = "你是电力行业智能问答助手，请严格按照知识库内容回答。" * 100

        # system_prompt 很大时留给历史消息的空间极少
        ctx = build_context(
            messages, max_tokens=500, system_prompt=long_system
        )

        # system_prompt 占满固定开销 → 历史被截断
        assert "[更早的对话内容已被截断]" in ctx

    def test_build_context_many_rounds_keeps_recent(self):
        """30 轮对话中 token 超限时保留最新轮次，最早被丢弃"""
        messages = []
        for i in range(30):
            messages.append(
                {"role": "user",
                 "content": f"第{i}轮用户问题，关于电力技术监督的具体内容咨询"}
            )
            messages.append(
                {"role": "assistant",
                 "content": f"第{i}轮助手回答，根据相关规程和处理办法进行详细说明"}
            )

        # 给一个较小的上限强制截断
        ctx = build_context(messages, max_tokens=1000)

        if "[更早的对话内容已被截断]" in ctx:
            # 截断：最旧的第0轮被丢弃
            assert "第0轮" not in ctx
            # 最新轮次保留
            assert "第29轮" in ctx
        else:
            # 未截断：全部保留
            assert "第0轮" in ctx
            assert "第29轮" in ctx

    def test_truncate_by_tokens_zero_limit(self):
        """max_tokens=0 时返回空列表"""
        messages = [{"role": "user", "content": "测试"}]
        result = truncate_by_tokens(messages, max_tokens=0)
        assert result == []

    def test_truncate_by_tokens_negative_limit(self):
        """max_tokens 为负数时返回空列表"""
        messages = [{"role": "user", "content": "测试"}]
        result = truncate_by_tokens(messages, max_tokens=-1)
        assert result == []

    def test_truncate_by_tokens_plain_strings(self):
        """消息为纯字符串（非 dict）时正常处理"""
        short_msg = "短消息"
        long_msg = "这是一条比较长的消息内容" * 20
        latest_msg = "最新消息"
        messages = [short_msg, long_msg, latest_msg]

        result = truncate_by_tokens(messages, max_tokens=50)

        # 至少保留 1 条最新消息
        assert len(result) >= 1
        # 最新消息应被保留
        last_content = (
            result[-1] if isinstance(result[-1], str)
            else result[-1].get("content", "")
        )
        assert "最新消息" in last_content

    def test_format_message_roles(self):
        """_format_message 角色标签映射：user→用户, assistant→助手, system→系统"""
        assert _format_message({"role": "user", "content": "你好"}) == "用户: 你好"

        assert _format_message(
            {"role": "assistant", "content": "你好，有什么可以帮助你的"}
        ) == "助手: 你好，有什么可以帮助你的"

        assert _format_message(
            {"role": "system", "content": "你是电力助手"}
        ) == "系统: 你是电力助手"

        # 未知角色回退为原始 role 值
        assert _format_message({"role": "bot", "content": "hello"}) == "bot: hello"


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
        assert event["phase"] == "done"

    def test_to_sse_event_keeps_start_phase(self):
        event = to_sse_event({"type": "generate", "message": "正在生成回答", "phase": "start"})
        assert event == {
            "type": "thinking",
            "step_type": "generate",
            "message": "正在生成回答",
            "elapsed_ms": None,
            "phase": "start",
        }

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
        assert step_payload["phase"] == "done"
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
                yield "custom", {
                    "type": "thinking",
                    "step_type": "intent",
                    "message": "正在识别用户意图",
                    "elapsed_ms": None,
                    "phase": "start",
                }
                yield "updates", {
                    "intent": {
                        "intent": "KNOWLEDGE_QA",
                        "thinking_steps": [first_step],
                    }
                }
                yield "custom", {
                    "type": "thinking",
                    "step_type": "generate",
                    "message": "正在生成回答",
                    "elapsed_ms": None,
                    "phase": "start",
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
        assert [(payload["step_type"], payload["phase"]) for payload in thinking_payloads] == [
            ("intent", "start"),
            ("intent", "done"),
            ("generate", "start"),
            ("generate", "done"),
        ]
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
