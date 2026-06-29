"""Passage reranking via SiliconFlow API."""
from typing import List, Optional
import httpx
from app.config import settings

class Reranker:
    def __init__(self):
        self.api_key = settings.siliconflow_api_key
        self.base_url = settings.siliconflow_base_url
        self.default_model = settings.rerank_model
        self._client = httpx.Client(timeout=30.0)
        self._mock = not self.api_key or self.api_key == "sk-test" or self.api_key.startswith("sk-your")

    def rerank(self, query: str, passages: List[str], top_k: Optional[int] = None, model: str = "") -> List[dict]:
        if not passages:
            return []
        if self._mock:
            import random as rnd
            n = len(passages)
            k = min(top_k or n, n)
            scores = sorted([rnd.Random(42 + i).random() for i in range(n)], reverse=True)
            indices = list(range(n))
            indices.sort(key=lambda i: scores[i], reverse=True)
            return [{"index": indices[i], "score": scores[indices[i]]} for i in range(k)]
        model = model or self.default_model
        resp = self._client.post(
            f"{self.base_url}/rerank",
            headers={"Authorization": f"Bearer {self.api_key}", "Content-Type": "application/json"},
            json={"model": model, "query": query, "documents": passages, "top_n": top_k or len(passages)}
        )
        resp.raise_for_status()
        data = resp.json()
        results = data.get("results", [])
        return [{"index": r["index"], "score": r["relevance_score"]} for r in results]
