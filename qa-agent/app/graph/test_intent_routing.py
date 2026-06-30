import asyncio
import unittest

from app.graph import nodes
from app.graph.nodes import (
    CHAT_INTENT,
    DOCUMENT_SEARCH_INTENT,
    KB_MANAGEMENT_INTENT,
    KNOWLEDGE_INTENT,
    REPORT_GENERATION_INTENT,
    TASK_ACTION_INTENT,
    intent_node,
)
from app.graph.workflow import route_by_intent


class IntentRoutingGoldenCases(unittest.TestCase):
    def _classify(self, question: str) -> dict:
        return asyncio.run(intent_node({"messages": [{"role": "user", "content": question}], "question": question}))

    def test_golden_cases(self):
        cases = [
            ("你好，请介绍一下你自己", CHAT_INTENT, "direct", False),
            ("什么是电力技术监督？", KNOWLEDGE_INTENT, "rag", False),
            ("帮我检索技术监督管理办法相关文档", DOCUMENT_SEARCH_INTENT, "rag", False),
            ("请生成一份技术监督月报", REPORT_GENERATION_INTENT, "clarify", True),
            ("帮我创建知识库并上传文档", KB_MANAGEMENT_INTENT, "clarify", True),
            ("提醒我明天提交审批", TASK_ACTION_INTENT, "clarify", True),
            ("帮我分析一下", KNOWLEDGE_INTENT, "clarify", True),
            ("查找文档并生成一份报告", REPORT_GENERATION_INTENT, "clarify", True),
        ]

        for question, expected_intent, expected_mode, expected_clarification in cases:
            with self.subTest(question=question):
                result = self._classify(question)
                self.assertEqual(result["intent"], expected_intent)
                self.assertEqual(result["mode"], expected_mode)
                self.assertEqual(route_by_intent(result), expected_mode)
                self.assertEqual(result["needs_clarification"], expected_clarification)
                self.assertIn("intent_confidence", result)
                self.assertIn("route_reason", result)
                self.assertIn("classification_source", result)

    def test_llm_unavailable_falls_back_to_clarify(self):
        original_api_key = nodes.settings.llm_api_key
        nodes.settings.llm_api_key = ""
        try:
            result = self._classify("alpha beta gamma")
        finally:
            nodes.settings.llm_api_key = original_api_key

        self.assertEqual(result["intent"], KNOWLEDGE_INTENT)
        self.assertEqual(result["mode"], "clarify")
        self.assertEqual(result["classification_source"], "fallback")
        self.assertTrue(result["needs_clarification"])

    def test_invalid_llm_payload_helpers_reject_bad_data(self):
        self.assertIsNone(nodes._extract_json_object("not json"))
        self.assertIsNone(nodes._normalize_llm_decision({"intent": "UNKNOWN", "confidence": 0.9}))


if __name__ == "__main__":
    unittest.main()
