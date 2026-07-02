from __future__ import annotations

import logging
import math
from dataclasses import dataclass
from typing import Any

from .elasticsearch_store import ElasticsearchChunkStore
from .qdrant_store import QdrantVectorStore
from .settings import Settings


logger = logging.getLogger(__name__)


@dataclass
class HybridCandidate:
    chunk_id: str
    document_id: str | None = None
    content: str | None = None
    chapter_path: str | None = None
    chunk_type: str | None = None
    chunk_index: int | None = None
    bm25_score: float | None = None
    similarity_score: float | None = None
    bm25_normalized: float = 0.0
    vector_normalized: float = 0.0
    hybrid_score: float = 0.0


class HybridSearchService:
    def __init__(self, settings: Settings, es_store: ElasticsearchChunkStore, qdrant_store: QdrantVectorStore) -> None:
        self._settings = settings
        self._es_store = es_store
        self._qdrant_store = qdrant_store

    def search(
        self,
        *,
        query: str,
        query_vector: list[float] | None,
        knowledge_base_ids: list[str],
        top_k: int,
        similarity_threshold: float,
        bm25_weight: float | None,
        vector_weight: float | None,
    ) -> list[dict[str, Any]]:
        weights = _resolve_weights(
            bm25_weight if bm25_weight is not None else self._settings.hybrid_bm25_weight,
            vector_weight if vector_weight is not None else self._settings.hybrid_vector_weight,
        )
        branch_limit = min(50, max(1, top_k) * max(1, self._settings.hybrid_candidate_multiplier))
        bm25_hits, bm25_failed = self._bm25_hits(query, knowledge_base_ids, branch_limit)
        vector_hits, vector_failed = self._vector_hits(query_vector, knowledge_base_ids, branch_limit, similarity_threshold)
        fused = fuse_hybrid_hits(bm25_hits, vector_hits, bm25_weight=weights[0], vector_weight=weights[1], top_k=top_k)
        if fused:
            return fused
        if bm25_failed and vector_failed:
            raise RuntimeError("Both BM25 and vector search branches failed")
        return []

    def _bm25_hits(self, query: str, knowledge_base_ids: list[str], branch_limit: int) -> tuple[list[dict[str, Any]], bool]:
        try:
            return self._es_store.search(query=query, knowledge_base_ids=knowledge_base_ids, top_k=branch_limit), False
        except Exception as exc:
            logger.warning("Elasticsearch BM25 search failed: %s", exc)
            return [], True

    def _vector_hits(
        self,
        query_vector: list[float] | None,
        knowledge_base_ids: list[str],
        branch_limit: int,
        similarity_threshold: float,
    ) -> tuple[list[dict[str, Any]], bool]:
        try:
            if query_vector is None:
                raise RuntimeError("Vector embedding is unavailable")
            return self._qdrant_store.search(
                query_vector=query_vector,
                knowledge_base_ids=knowledge_base_ids,
                top_k=branch_limit,
                similarity_threshold=similarity_threshold,
            ), False
        except Exception as exc:
            logger.warning("Qdrant vector search failed: %s", exc)
            return [], True


def fuse_hybrid_hits(
    bm25_hits: list[dict[str, Any]],
    vector_hits: list[dict[str, Any]],
    *,
    bm25_weight: float,
    vector_weight: float,
    top_k: int,
) -> list[dict[str, Any]]:
    candidates: dict[str, HybridCandidate] = {}
    max_bm25 = _max_positive(bm25_hits, "bm25Score")
    max_vector = _max_positive(vector_hits, "similarityScore")
    for hit in bm25_hits:
        chunk_id = hit.get("chunkId")
        if not chunk_id:
            continue
        candidate = candidates.setdefault(str(chunk_id), HybridCandidate(chunk_id=str(chunk_id)))
        _fill_candidate(candidate, hit)
        candidate.bm25_score = _float_or_none(hit.get("bm25Score"))
        candidate.bm25_normalized = _normalize(candidate.bm25_score, max_bm25)
    for hit in vector_hits:
        chunk_id = hit.get("chunkId")
        if not chunk_id:
            continue
        candidate = candidates.setdefault(str(chunk_id), HybridCandidate(chunk_id=str(chunk_id)))
        _fill_candidate(candidate, hit)
        candidate.similarity_score = _float_or_none(hit.get("similarityScore"))
        candidate.vector_normalized = _normalize(candidate.similarity_score, max_vector)
    ordered: list[HybridCandidate] = []
    for candidate in candidates.values():
        candidate.hybrid_score = bm25_weight * candidate.bm25_normalized + vector_weight * candidate.vector_normalized
        ordered.append(candidate)
    ordered.sort(key=lambda item: (-item.hybrid_score, -item.bm25_normalized, -item.vector_normalized, item.chunk_index if item.chunk_index is not None else math.inf, item.chunk_id))
    return [_to_hit(candidate) for candidate in ordered[: max(1, top_k)]]


def _resolve_weights(bm25_weight: float, vector_weight: float) -> tuple[float, float]:
    if not math.isfinite(bm25_weight) or not math.isfinite(vector_weight) or bm25_weight < 0 or vector_weight < 0:
        raise ValueError("Hybrid weights must be non-negative")
    total = bm25_weight + vector_weight
    if total <= 0:
        raise ValueError("Hybrid weights cannot both be zero")
    return bm25_weight / total, vector_weight / total


def _fill_candidate(candidate: HybridCandidate, hit: dict[str, Any]) -> None:
    candidate.document_id = candidate.document_id or hit.get("documentId")
    candidate.content = candidate.content or hit.get("content")
    candidate.chapter_path = candidate.chapter_path or hit.get("chapterPath")
    candidate.chunk_type = candidate.chunk_type or hit.get("chunkType")
    if candidate.chunk_index is None:
        candidate.chunk_index = _int_or_none(hit.get("chunkIndex"))


def _max_positive(hits: list[dict[str, Any]], field: str) -> float:
    scores = [_float_or_none(hit.get(field)) for hit in hits]
    return max((score for score in scores if score is not None and score > 0), default=0.0)


def _normalize(score: float | None, max_score: float) -> float:
    if score is None or score <= 0 or max_score <= 0:
        return 0.0
    return score / max_score


def _float_or_none(value: Any) -> float | None:
    if value is None:
        return None
    try:
        return float(value)
    except (TypeError, ValueError):
        return None


def _int_or_none(value: Any) -> int | None:
    if value is None:
        return None
    try:
        return int(value)
    except (TypeError, ValueError):
        return None


def _to_hit(candidate: HybridCandidate) -> dict[str, Any]:
    return {
        "chunkId": candidate.chunk_id,
        "documentId": candidate.document_id,
        "content": candidate.content,
        "chapterPath": candidate.chapter_path,
        "chunkType": candidate.chunk_type,
        "similarityScore": candidate.similarity_score,
        "bm25Score": candidate.bm25_score,
        "hybridScore": candidate.hybrid_score,
    }
