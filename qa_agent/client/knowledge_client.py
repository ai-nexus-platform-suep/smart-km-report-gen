"""HTTP-first knowledge retrieval adapter (人员 A 独占)."""

from typing import Any

import httpx

from qa_agent.core.config import settings


def normalize_document(raw_document: dict[str, Any]) -> dict:
    metadata = raw_document.get("metadata")
    score = raw_document.get("score", raw_document.get("similarity", 0.0))

    try:
        normalized_score = float(score)
    except (TypeError, ValueError):
        normalized_score = 0.0

    return {
        "doc_id": str(raw_document.get("doc_id") or raw_document.get("docId") or ""),
        "doc_name": str(raw_document.get("doc_name") or raw_document.get("docName") or ""),
        "kb_id": raw_document.get("kb_id", raw_document.get("kbId")),
        "snippet": str(raw_document.get("snippet") or raw_document.get("content") or ""),
        "score": normalized_score,
        "metadata": metadata if isinstance(metadata, dict) else {},
    }


def _extract_documents(payload: Any) -> list[dict]:
    if isinstance(payload, list):
        return [normalize_document(item) for item in payload if isinstance(item, dict)]

    if not isinstance(payload, dict):
        return []

    for key in ("documents", "data", "items", "results"):
        value = payload.get(key)
        if isinstance(value, list):
            return [normalize_document(item) for item in value if isinstance(item, dict)]
        if isinstance(value, dict):
            nested = _extract_documents(value)
            if nested:
                return nested
    return []


async def search_knowledge(
    query: str,
    selected_kb_ids: list[int] | None = None,
    top_k: int | None = None,
    similarity_threshold: float | None = None,
    embedding: list[float] | None = None,
) -> list[dict]:
    if not settings.knowledge_search_url or not query:
        return []

    request_payload = {
        "query": query,
        "selected_kb_ids": selected_kb_ids or [],
        "top_k": top_k or settings.default_top_k,
        "similarity_threshold": similarity_threshold or settings.default_similarity_threshold,
        "embedding": embedding,
    }

    try:
        async with httpx.AsyncClient(timeout=settings.retrieval_timeout) as client:
            response = await client.post(settings.knowledge_search_url, json=request_payload)
            response.raise_for_status()
            payload = response.json()
    except (httpx.HTTPError, ValueError):
        return []

    return _extract_documents(payload)
