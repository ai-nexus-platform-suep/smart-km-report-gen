from __future__ import annotations

from typing import Any

import requests

from .backend_client import BackendClient, EmbeddingConfig
from .settings import Settings


class Embedder:
    def __init__(self, settings: Settings, backend_client: BackendClient | None = None) -> None:
        self._settings = settings
        self._backend_client = backend_client or BackendClient(settings)

    def embed_texts(self, texts: list[str], model: str | None = None, config: EmbeddingConfig | None = None) -> tuple[list[list[float]], int]:
        clean_texts = [text for text in texts if text and text.strip()]
        if len(clean_texts) != len(texts):
            raise ValueError("Embedding texts must be non-empty")
        embedding_config = config or self._backend_client.get_embedding_config()
        request_model = model or embedding_config.model_name
        vectors: list[list[float]] = []
        for start in range(0, len(clean_texts), self._settings.embedding_batch_size):
            batch = clean_texts[start : start + self._settings.embedding_batch_size]
            vectors.extend(self._call_embedding_api(batch, request_model, embedding_config))
        self._validate_vectors(vectors, embedding_config.dimension)
        return vectors, embedding_config.dimension

    def _call_embedding_api(self, texts: list[str], model: str, config: EmbeddingConfig) -> list[list[float]]:
        headers = {"Content-Type": "application/json"}
        if config.api_key:
            headers["Authorization"] = f"Bearer {config.api_key}"
        payload: dict[str, object] = {"model": model, "input": texts}
        if model == "text-embedding-v4":
            payload["dimensions"] = config.dimension
        response = requests.post(
            _embedding_url(config.api_url),
            json=payload,
            headers=headers,
            timeout=self._settings.embedding_request_timeout_seconds,
        )
        response.raise_for_status()
        body = response.json()
        data = body.get("data") if isinstance(body, dict) else None
        if not isinstance(data, list):
            raise RuntimeError("Embedding provider response missing data")
        vectors: list[list[float]] = []
        for item in data:
            vector = _extract_embedding(item)
            vectors.append(vector)
        if len(vectors) != len(texts):
            raise RuntimeError("Embedding provider returned unexpected vector count")
        return vectors

    @staticmethod
    def _validate_vectors(vectors: list[list[float]], dimension: int) -> None:
        for vector in vectors:
            if len(vector) != dimension:
                raise RuntimeError(f"Embedding dimension mismatch: expected {dimension}, got {len(vector)}")


def _extract_embedding(item: Any) -> list[float]:
    if isinstance(item, dict):
        embedding = item.get("embedding")
    else:
        embedding = item
    if not isinstance(embedding, list):
        raise RuntimeError("Embedding item missing vector")
    return [float(value) for value in embedding]


def _embedding_url(api_url: str) -> str:
    base = api_url.rstrip("/")
    if base.endswith("/embeddings"):
        return base
    return base + "/embeddings"
