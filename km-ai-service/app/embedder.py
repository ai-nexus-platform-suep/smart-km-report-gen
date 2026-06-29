"""Text embedding via SiliconFlow API."""
import time
from typing import List, Optional
import httpx
from app.config import settings

class Embedder:
    def __init__(self):
        self.api_key = settings.siliconflow_api_key
        self.base_url = settings.siliconflow_base_url
        self.default_model = settings.embed_model
        self.dimension = settings.milvus_dimension
        self._client = httpx.Client(timeout=60.0)
        self._mock = not self.api_key or self.api_key == "sk-test" or self.api_key.startswith("sk-your")

    def embed(self, texts: List[str], model: str = "") -> List[List[float]]:
        if not texts:
            return []
        if self._mock:
            return self._mock_embed(texts)
        model = model or self.default_model
        resp = self._client.post(
            f"{self.base_url}/embeddings",
            headers={"Authorization": f"Bearer {self.api_key}", "Content-Type": "application/json"},
            json={"model": model, "input": texts, "encoding_format": "float"}
        )
        resp.raise_for_status()
        data = resp.json()
        vectors = [item["embedding"] for item in data["data"]]
        return vectors

    def _mock_embed(self, texts):
        import random, math
        dim = self.dimension
        rng = random.Random(42)
        return [[rng.gauss(0, 1) / math.sqrt(dim) for _ in range(dim)] for _ in texts]
