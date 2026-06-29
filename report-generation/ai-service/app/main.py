"""AI proxy service — Prompt hosting + LLM calls for backend integration."""

from __future__ import annotations

import json
import logging
import re
from pathlib import Path
from typing import Any, Optional

from dotenv import load_dotenv
from fastapi import FastAPI, HTTPException, Request

load_dotenv(Path(__file__).resolve().parent.parent / ".env")
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import StreamingResponse
from pydantic import BaseModel, Field

from app.llm_client import LlmClient
from app.prompts import build_outline_messages, build_section_messages

app = FastAPI(title="Power Report AI Service", version="1.0.0")
logger = logging.getLogger("power-report-ai")
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
    reportType: Optional[str] = None
    reportTypeLabel: Optional[str] = None
    subject: Optional[str] = ""
    powerPlant: Optional[str] = ""
    specialty: Optional[str] = ""
    reportYear: Optional[int] = None
    sectionNumber: Optional[str] = None
    sectionTitle: Optional[str] = None
    sectionLevel: Optional[int] = 2
    promptHint: Optional[str] = None
    outlineContext: Optional[str] = None
    existingContentMarkdown: Optional[str] = None
    userHint: Optional[str] = None
    regenerate: bool = False


def _normalize_section_payload(raw_payload: dict[str, Any]) -> dict[str, Any]:
    payload = SectionStreamRequest.model_validate(raw_payload).model_dump()
    payload["reportType"] = payload.get("reportType") or "SUMMER_PEAK_CHECK"
    payload["subject"] = payload.get("subject") or ""
    payload["powerPlant"] = payload.get("powerPlant") or ""
    payload["specialty"] = payload.get("specialty") or ""
    payload["sectionNumber"] = payload.get("sectionNumber") or "1"
    payload["sectionTitle"] = payload.get("sectionTitle") or "未命名章节"
    payload["sectionLevel"] = payload.get("sectionLevel") or 2
    payload["promptHint"] = payload.get("promptHint") or ""
    payload["outlineContext"] = payload.get("outlineContext") or ""
    payload["existingContentMarkdown"] = payload.get("existingContentMarkdown") or ""
    payload["userHint"] = payload.get("userHint") or ""
    if not payload.get("reportTypeLabel"):
        labels = {
            "SUMMER_PEAK_CHECK": "迎峰度夏检查报告",
            "COAL_INVENTORY_AUDIT": "煤库库存审计报告",
        }
        payload["reportTypeLabel"] = labels.get(payload["reportType"], payload["reportType"])
    return payload


async def _read_json_body(request: Request) -> dict[str, Any]:
    raw_body = await request.body()
    if not raw_body:
        logger.warning("Section stream request body is empty; fallback defaults will be used.")
        return {}

    try:
        parsed = json.loads(raw_body.decode("utf-8"))
    except (UnicodeDecodeError, json.JSONDecodeError) as exc:
        raise HTTPException(status_code=400, detail=f"请求体不是合法 JSON: {exc}") from exc

    if not isinstance(parsed, dict):
        raise HTTPException(status_code=400, detail="请求体必须是 JSON 对象")
    return parsed


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
async def stream_section(request: Request):
    payload = _normalize_section_payload(await _read_json_body(request))

    messages = build_section_messages(payload, regenerate=payload.get("regenerate", False))

    def sse_event(event: str, data: str) -> str:
        lines = str(data).replace("\r\n", "\n").replace("\r", "\n").split("\n")
        payload_lines = "".join(f"data: {line}\n" for line in lines)
        return f"event: {event}\n{payload_lines}\n"

    async def event_generator():
        try:
            async for chunk in llm.chat_stream(messages):
                yield sse_event("content", chunk)
            yield sse_event("end", "[END]")
        except Exception as exc:
            yield sse_event("error", str(exc))

    return StreamingResponse(
        event_generator(),
        media_type="text/event-stream; charset=utf-8",
        headers={
            "Cache-Control": "no-cache",
            "Connection": "keep-alive",
            "X-Accel-Buffering": "no",
        },
    )
