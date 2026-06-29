"""OpenAI-compatible LLM client with streaming and mock mode."""

from __future__ import annotations

import asyncio
import json
import os
from typing import AsyncIterator

import httpx

MOCK_OUTLINE_SUMMER = {
    "success": True,
    "data": {
        "outline": [
            {
                "number": "1",
                "title": "检查概况",
                "level": 1,
                "promptHint": "说明检查背景、范围和依据",
                "children": [
                    {
                        "number": "1.1",
                        "title": "检查背景",
                        "level": 2,
                        "promptHint": "结合迎峰度夏政策与电厂负荷特点",
                        "children": [],
                    },
                    {
                        "number": "1.2",
                        "title": "检查范围",
                        "level": 2,
                        "promptHint": "明确检查设备与管理范围",
                        "children": [],
                    },
                ],
            },
            {
                "number": "2",
                "title": "设备运行与安全保障情况",
                "level": 1,
                "promptHint": "概述主设备运行状态",
                "children": [
                    {
                        "number": "2.1",
                        "title": "主设备运行情况",
                        "level": 2,
                        "promptHint": "需含设备检查项表格",
                        "children": [],
                    }
                ],
            },
        ]
    },
}

MOCK_SECTION_TEXT = (
    "根据{year}年迎峰度夏保供工作要求，{plant}组织开展了专项安全检查。"
    "本次检查聚焦主设备运行可靠性、电气系统完整性及热控保护有效性，"
    "整体运行平稳，未发现危及电网安全的重大隐患。\n\n"
    "| 序号 | 检查项目 | 检查结果 | 备注 |\n"
    "| --- | --- | --- | --- |\n"
    "| 1 | 主变压器运行 | 正常 | 油温、油位均在标准范围 |\n"
    "| 2 | 厂用电系统 | 正常 | 双电源切换试验合格 |\n"
)


class LlmClient:
    def __init__(self) -> None:
        self.api_url = os.getenv("LLM_API_URL", "https://api.deepseek.com/v1/chat/completions")
        self.api_key = os.getenv("LLM_API_KEY", "")
        self.model = os.getenv("LLM_MODEL", "deepseek-chat")
        self.timeout = float(os.getenv("LLM_TIMEOUT_SECONDS", "60"))
        self.mock = os.getenv("LLM_MOCK", "false").lower() in ("1", "true", "yes")

    async def chat_completion(self, messages: list[dict[str, str]]) -> str:
        if self.mock:
            return json.dumps(MOCK_OUTLINE_SUMMER, ensure_ascii=False)

        headers = {
            "Authorization": f"Bearer {self.api_key}",
            "Content-Type": "application/json",
        }
        body = {
            "model": self.model,
            "messages": messages,
            "temperature": 0.3,
            "response_format": {"type": "json_object"},
        }
        async with httpx.AsyncClient(timeout=self.timeout) as client:
            resp = await client.post(self.api_url, headers=headers, json=body)
            resp.raise_for_status()
            data = resp.json()
            return data["choices"][0]["message"]["content"]

    async def chat_stream(self, messages: list[dict[str, str]]) -> AsyncIterator[str]:
        if self.mock:
            text = MOCK_SECTION_TEXT.format(
                year=messages[-1]["content"][:4] if messages else "2026",
                plant="示例电厂",
            )
            chunk_size = 8
            for i in range(0, len(text), chunk_size):
                yield text[i : i + chunk_size]
                await asyncio.sleep(0.05)
            return

        headers = {
            "Authorization": f"Bearer {self.api_key}",
            "Content-Type": "application/json",
        }
        body = {
            "model": self.model,
            "messages": messages,
            "temperature": 0.5,
            "stream": True,
        }
        async with httpx.AsyncClient(timeout=self.timeout) as client:
            async with client.stream("POST", self.api_url, headers=headers, json=body) as resp:
                resp.raise_for_status()
                async for line in resp.aiter_lines():
                    if not line or not line.startswith("data: "):
                        continue
                    payload = line[6:].strip()
                    if payload == "[DONE]":
                        break
                    try:
                        chunk = json.loads(payload)
                        delta = chunk["choices"][0].get("delta", {})
                        content = delta.get("content")
                        if content:
                            yield content
                    except (json.JSONDecodeError, KeyError, IndexError):
                        continue
