from __future__ import annotations

from dataclasses import dataclass
from typing import Any

import requests

from .chunker import Chunk
from .settings import Settings


@dataclass(frozen=True)
class EmbeddingConfig:
    model_name: str
    api_url: str
    api_key: str
    dimension: int


class BackendClient:
    def __init__(self, settings: Settings) -> None:
        self._settings = settings
        self._embedding_config_url = settings.backend_embedding_config_url
        self._replace_chunks_url_template = settings.backend_replace_chunks_url_template

    def get_embedding_config(self) -> EmbeddingConfig:
        if self._settings.embedding_config_source == "env":
            return _embedding_config_from_env(self._settings)
        response = requests.get(self._embedding_config_url, timeout=10)
        response.raise_for_status()
        body = response.json()
        if not isinstance(body, dict) or body.get("code") not in {0, None}:
            raise RuntimeError(f"Embedding config request failed: {body.get('message') if isinstance(body, dict) else 'invalid response'}")
        data = body.get("data") if isinstance(body, dict) else None
        if not isinstance(data, dict):
            raise RuntimeError("Embedding config response missing data")
        model_name = str(data.get("modelName") or data.get("model") or "").strip()
        api_url = str(data.get("apiUrl") or "").strip()
        api_key = str(data.get("apiKey") or "").strip()
        dimension = int(data.get("dimension") or 0)
        if not model_name or not api_url or dimension <= 0:
            raise RuntimeError("Embedding config is incomplete")
        return EmbeddingConfig(model_name=model_name, api_url=api_url, api_key=api_key, dimension=dimension)

    def replace_chunks(self, *, document_id: str, kb_id: str, job_id: str | None, chunks: list[Chunk]) -> int:
        url = self._replace_chunks_url_template.format(document_id=document_id)
        payload: dict[str, Any] = {
            "jobId": job_id,
            "kbId": kb_id,
            "chunks": [
                {
                    "id": chunk.id,
                    "content": chunk.content,
                    "chapterPath": chunk.chapter_path,
                    "chunkIndex": chunk.chunk_index,
                    "chunkType": chunk.chunk_type,
                    "vectorId": chunk.vector_id,
                }
                for chunk in chunks
            ],
        }
        response = requests.post(url, json=payload, timeout=30)
        response.raise_for_status()
        body = response.json()
        if not isinstance(body, dict):
            raise RuntimeError("Replace chunks returned invalid response")
        if body.get("code") != 0:
            raise RuntimeError(f"Replace chunks failed: {body.get('message')}")
        data = body.get("data") or {}
        return int(data.get("chunkCount") or len(chunks))


def _embedding_config_from_env(settings: Settings) -> EmbeddingConfig:
    if not settings.embedding_model_name or not settings.embedding_api_url or settings.embedding_dimension <= 0:
        raise RuntimeError("Environment embedding config is incomplete")
    return EmbeddingConfig(
        model_name=settings.embedding_model_name,
        api_url=settings.embedding_api_url,
        api_key=settings.embedding_api_key,
        dimension=settings.embedding_dimension,
    )
