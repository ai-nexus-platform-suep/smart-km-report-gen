from __future__ import annotations

from typing import Any

from qdrant_client import QdrantClient
from qdrant_client.http import models

from .chunker import Chunk
from .settings import Settings


class QdrantVectorStore:
    def __init__(self, settings: Settings) -> None:
        self._collection = settings.qdrant_collection
        self._client = QdrantClient(
            url=settings.qdrant_url,
            api_key=settings.qdrant_api_key or None,
            timeout=settings.qdrant_timeout_seconds,
        )

    def ensure_collection(self, dimension: int) -> None:
        collections = self._client.get_collections().collections
        exists = any(collection.name == self._collection for collection in collections)
        if not exists:
            self._client.create_collection(
                collection_name=self._collection,
                vectors_config=models.VectorParams(size=dimension, distance=models.Distance.COSINE),
            )
            return
        info = self._client.get_collection(self._collection)
        vectors = info.config.params.vectors
        size = vectors.size if isinstance(vectors, models.VectorParams) else None
        if size != dimension:
            raise RuntimeError(f"Qdrant collection dimension mismatch: expected {dimension}, got {size}")

    def replace_document_vectors(
        self,
        *,
        document_id: str,
        kb_id: str,
        chunks: list[Chunk],
        vectors: list[list[float]],
        delete_existing: bool = True,
        source_object: str | None = None,
        metadata_object: str | None = None,
    ) -> None:
        if len(chunks) != len(vectors):
            raise ValueError("Chunk and vector counts differ")
        if delete_existing:
            self.delete_by_document(document_id)
        points = []
        for chunk, vector in zip(chunks, vectors):
            points.append(
                models.PointStruct(
                    id=_point_id(document_id, chunk.id),
                    vector=vector,
                    payload={
                        "chunk_id": chunk.id,
                        "document_id": document_id,
                        "kb_id": kb_id,
                        "content": chunk.content,
                        "chapter_path": chunk.chapter_path,
                        "chunk_index": chunk.chunk_index,
                        "chunk_type": chunk.chunk_type,
                        "source_object": source_object,
                        "metadata_object": metadata_object,
                    },
                )
            )
        self._client.upsert(collection_name=self._collection, points=points, wait=True)

    def delete_by_document(self, document_id: str) -> None:
        self._client.delete(
            collection_name=self._collection,
            points_selector=models.FilterSelector(
                filter=models.Filter(
                    must=[models.FieldCondition(key="document_id", match=models.MatchValue(value=document_id))]
                )
            ),
            wait=True,
        )

    def search(self, *, query_vector: list[float], knowledge_base_ids: list[str], top_k: int, similarity_threshold: float) -> list[dict[str, Any]]:
        query_filter = None
        if knowledge_base_ids:
            query_filter = models.Filter(
                must=[models.FieldCondition(key="kb_id", match=models.MatchAny(any=knowledge_base_ids))]
            )
        hits = self._client.search(
            collection_name=self._collection,
            query_vector=query_vector,
            query_filter=query_filter,
            limit=top_k,
            with_payload=True,
        )
        results: list[dict[str, Any]] = []
        for hit in hits:
            if hit.score is None or hit.score < similarity_threshold:
                continue
            payload = hit.payload or {}
            results.append(
                {
                    "chunkId": payload.get("chunk_id"),
                    "documentId": payload.get("document_id"),
                    "content": payload.get("content"),
                    "chapterPath": payload.get("chapter_path"),
                    "chunkIndex": payload.get("chunk_index"),
                    "chunkType": payload.get("chunk_type"),
                    "similarityScore": float(hit.score),
                }
            )
        return results


def _point_id(document_id: str, chunk_id: str) -> str:
    try:
        import uuid

        uuid.UUID(chunk_id)
        return chunk_id
    except ValueError:
        import uuid

        return str(uuid.uuid5(uuid.NAMESPACE_URL, f"{document_id}:{chunk_id}"))
