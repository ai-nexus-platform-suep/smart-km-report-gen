import json
import os
import sys
import unittest
from pathlib import Path

try:
    import fastapi  # noqa: F401
    import httpx  # noqa: F401
    import pydantic  # noqa: F401
    import dotenv  # noqa: F401
except ModuleNotFoundError as exc:
    raise unittest.SkipTest(f"ai-service dependency is not installed: {exc}") from exc

REPORT_GENERATION_DIR = Path(__file__).resolve().parents[2]
AI_SERVICE_DIR = REPORT_GENERATION_DIR / "ai-service"
sys.path.insert(0, str(AI_SERVICE_DIR))

os.environ.setdefault("LLM_MOCK", "true")

from app.llm_client import LlmClient  # noqa: E402
from app.prompts import fill_template  # noqa: E402


class AiServiceTest(unittest.IsolatedAsyncioTestCase):

    def test_fill_template_serializes_nested_values(self):
        result = fill_template("subject={subject}; context={context}", {
            "subject": "summer peak",
            "context": {"year": 2026, "items": ["a", "b"]},
        })

        self.assertIn("summer peak", result)
        self.assertIn('"year": 2026', result)
        self.assertIn('"items"', result)

    def test_llm_client_normalizes_v1_base_url(self):
        client = LlmClient()

        self.assertEqual(
            "https://api.deepseek.com/v1/chat/completions",
            client._normalize_api_url("https://api.deepseek.com/v1"),
        )

    async def test_mock_outline_completion_returns_outline_json(self):
        os.environ["LLM_MOCK"] = "true"
        client = LlmClient()

        raw = await client.chat_completion([{"role": "user", "content": "outline"}])
        data = json.loads(raw)

        self.assertTrue(data["success"])
        self.assertIn("outline", data["data"])


if __name__ == "__main__":
    unittest.main()
