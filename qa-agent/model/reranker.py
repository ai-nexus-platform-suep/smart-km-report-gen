"""重排序模型 (人员 A 独占)"""


async def rerank(query: str, documents: list[dict], top_k: int = 5) -> list[dict]:
    del query
    ranked = sorted(
        documents,
        key=lambda document: float(document.get("score") or 0.0),
        reverse=True,
    )
    return ranked[:top_k]
