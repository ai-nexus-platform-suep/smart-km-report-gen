"""向量化模型 (人员 A 独占)"""

import httpx

from app.core.config import settings


async def embed_texts(texts: list[str]) -> list[list[float]]:
    clean_texts = [text for text in texts if text]
    if not clean_texts or not settings.embedding_api_key:
        return []

    try:
        async with httpx.AsyncClient(timeout=settings.llm_timeout) as client:
            response = await client.post(
                f"{settings.embedding_api_url.rstrip('/')}/embeddings",
                headers={"Authorization": f"Bearer {settings.embedding_api_key}"},
                json={"model": settings.embedding_model_name, "input": clean_texts},
            )
            response.raise_for_status()
            payload = response.json()
    except (httpx.HTTPError, ValueError):
        return []

    data = payload.get("data") if isinstance(payload, dict) else None
    if not isinstance(data, list):
        return []

    embeddings: list[list[float]] = []
    for item in data:
        embedding = item.get("embedding") if isinstance(item, dict) else None
        if isinstance(embedding, list):
            embeddings.append([float(value) for value in embedding])
    return embeddings


async def embed_query(query: str) -> list[float]:
    embeddings = await embed_texts([query])
    return embeddings[0] if embeddings else []
