"""重排序模型：调用 Cross-Encoder API 对检索结果进行语义重排序"""

import httpx

from app.core.config import settings


async def rerank(query: str, documents: list[dict], top_k: int = 5) -> list[dict]:
    """调用重排序 API 按与 query 的相关性重新排序，返回 top_k 条。

    若未配置 reranker API，回退到基于检索分数的排序。
    """
    if not documents:
        return []

    if not settings.reranker_api_url or not settings.reranker_api_key:
        return _fallback_rerank(documents, top_k)

    try:
        scores = await _call_reranker_api(query, documents)
    except (httpx.HTTPError, ValueError, KeyError, IndexError):
        return _fallback_rerank(documents, top_k)

    for document, score in zip(documents, scores):
        document["rerank_score"] = score

    documents.sort(key=lambda d: float(d.get("rerank_score") or 0.0), reverse=True)
    result = documents[:top_k]
    # 最终以 rerank 分数作为 score
    for document in result:
        document["score"] = document.get("rerank_score", document.get("score", 0.0))
    return result


def _fallback_rerank(documents: list[dict], top_k: int) -> list[dict]:
    """无 reranker API 时按原始检索分数排序"""
    ranked = sorted(
        documents,
        key=lambda d: float(d.get("score") or 0.0),
        reverse=True,
    )
    return ranked[:top_k]


async def _call_reranker_api(query: str, documents: list[dict]) -> list[float]:
    """调用 reranker API，返回每个文档的相关性分数列表"""
    doc_texts = [
        d.get("snippet") or d.get("content") or ""
        for d in documents
    ]

    async with httpx.AsyncClient(timeout=settings.llm_timeout) as client:
        response = await client.post(
            f"{settings.reranker_api_url.rstrip('/')}/rerank",
            headers={"Authorization": f"Bearer {settings.reranker_api_key}"},
            json={
                "model": settings.reranker_model_name,
                "query": query,
                "documents": doc_texts,
                "top_n": len(documents),
            },
        )
        response.raise_for_status()
        payload = response.json()

    results = payload.get("results") or []
    score_map = {item["index"]: float(item["relevance_score"]) for item in results}
    return [score_map.get(i, 0.0) for i in range(len(documents))]
