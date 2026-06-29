"""集成测试 (人员 C 独占)

覆盖: 上下文管理、思考过程、引用溯源 三个模块
运行: python -m pytest qa-agent/tests/test_chat.py -v
"""

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
