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

    def rerank(self, query: str, passages: List[str], top_k: Optional[int] = None, model: str = "") -> List[dict]:
        if not passages:
            return []
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
