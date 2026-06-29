"""AI proxy service — Prompt hosting + LLM calls for backend integration."""

from __future__ import annotations

import json
import re
from pathlib import Path
from typing import Any, Optional

from dotenv import load_dotenv
from fastapi import FastAPI, HTTPException

load_dotenv(Path(__file__).resolve().parent.parent / ".env")
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import StreamingResponse
from pydantic import BaseModel, Field

from app.llm_client import LlmClient
from app.prompts import build_outline_messages, build_section_messages

app = FastAPI(title="Power Report AI Service", version="1.0.0")
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)

llm = LlmClient()


class OutlineRequest(BaseModel):
    reportType: str
    reportTypeLabel: Optional[str] = None
    subject: str = ""
    name: Optional[str] = None
    specialty: str = ""
    powerPlant: str = ""
    reportYear: Optional[int] = None
    context: Optional[dict[str, Any]] = None


class SectionStreamRequest(BaseModel):
    reportType: str
    reportTypeLabel: Optional[str] = None
    subject: str = ""
    powerPlant: str = ""
    specialty: str = ""
    reportYear: Optional[int] = None
    sectionNumber: str
    sectionTitle: str
    sectionLevel: int = 2
    promptHint: Optional[str] = None
    outlineContext: Optional[str] = None
    existingContentMarkdown: Optional[str] = None
    userHint: Optional[str] = None
    regenerate: bool = False


def _strip_code_fence(text: str) -> str:
    stripped = text.strip()
    match = re.search(r"```(?:json)?\s*([\s\S]*?)```", stripped)
    if match:
        return match.group(1).strip()
    return stripped


def _parse_outline_json(raw: str) -> dict[str, Any]:
    cleaned = _strip_code_fence(raw)
    try:
        parsed = json.loads(cleaned)
    except json.JSONDecodeError as exc:
        raise HTTPException(status_code=502, detail=f"LLM 返回非合法 JSON: {exc}") from exc

    if "data" in parsed and "outline" in parsed.get("data", {}):
        return parsed
    if "outline" in parsed:
        return {"success": True, "data": {"outline": parsed["outline"]}}
    raise HTTPException(status_code=502, detail="LLM 返回缺少 outline 字段")


@app.get("/api/health")
async def health():
    return {"status": "ok", "scope": "ai-service", "mock": llm.mock}


@app.post("/api/ai/outline/generate")
async def generate_outline(body: OutlineRequest):
    payload = body.model_dump()
    if not payload.get("reportTypeLabel"):
        labels = {
            "SUMMER_PEAK_CHECK": "迎峰度夏检查报告",
            "COAL_INVENTORY_AUDIT": "煤库库存审计报告",
        }
        payload["reportTypeLabel"] = labels.get(body.reportType, body.reportType)

    messages = build_outline_messages(payload)
    try:
        raw = await llm.chat_completion(messages)
    except Exception as exc:
        raise HTTPException(status_code=502, detail=f"LLM 调用失败: {exc}") from exc

    return _parse_outline_json(raw)


@app.post("/api/ai/section/stream")
async def stream_section(body: SectionStreamRequest):
    payload = body.model_dump()
    if not payload.get("reportTypeLabel"):
        labels = {
            "SUMMER_PEAK_CHECK": "迎峰度夏检查报告",
            "COAL_INVENTORY_AUDIT": "煤库库存审计报告",
        }
        payload["reportTypeLabel"] = labels.get(body.reportType, body.reportType)

    messages = build_section_messages(payload, regenerate=body.regenerate)

    async def event_generator():
        try:
            async for chunk in llm.chat_stream(messages):
                yield f"event: content\ndata: {chunk}\n\n"
            yield "event: end\ndata: [END]\n\n"
        except Exception as exc:
            yield f"event: error\ndata: {str(exc)}\n\n"

    return StreamingResponse(
        event_generator(),
        media_type="text/event-stream; charset=utf-8",
        headers={
            "Cache-Control": "no-cache",
            "Connection": "keep-alive",
            "X-Accel-Buffering": "no",
        },
    )
