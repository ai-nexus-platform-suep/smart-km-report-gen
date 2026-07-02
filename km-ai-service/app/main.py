from __future__ import annotations

import logging

from fastapi import FastAPI, HTTPException
from pydantic import BaseModel, Field

from .backend_client import BackendClient
from .batch_reindex import BatchReindexRequest, BatchReindexService
from .elasticsearch_store import ElasticsearchChunkStore
from .embedder import Embedder
from .hybrid_search import HybridSearchService
from .qdrant_store import QdrantVectorStore
from .settings import Settings


app = FastAPI(title="km-ai-service", version="0.1.0")
_batch_reindex_service: BatchReindexService | None = None
logger = logging.getLogger(__name__)


def _get_batch_reindex_service(settings: Settings) -> BatchReindexService:
    global _batch_reindex_service
    if _batch_reindex_service is None:
        _batch_reindex_service = BatchReindexService(settings)
    return _batch_reindex_service


@app.get("/internal/health")
def health() -> dict[str, object]:
    settings = Settings.from_env()
    return {
        "code": 0,
        "message": "ok",
        "data": {
            "service": "km-ai-service",
            "status": "UP",
            "queue": settings.rabbitmq_queue,
            "max_concurrent_parse_jobs": settings.max_concurrent_parse_jobs,
            "rabbitmq_prefetch_count": settings.rabbitmq_prefetch_count,
            "mineru": {
                "backend": settings.mineru_backend,
                "mode": "paid-v4-file" if settings.mineru_backend == "paid_v4" else "agent-file",
                "agent_api_base_url": settings.mineru_agent_api_base_url,
                "paid_api_base_url": settings.mineru_paid_api_base_url,
                "api_token_configured": bool(settings.mineru_api_token),
                "model_version": settings.mineru_model_version,
                "page_ranges": settings.mineru_page_ranges or None,
                "method": settings.mineru_method,
                "lang": settings.mineru_lang,
                "enable_table": settings.mineru_enable_table,
                "enable_formula": settings.mineru_enable_formula,
                "timeout_seconds": settings.mineru_timeout_seconds,
                "poll_interval_seconds": settings.mineru_poll_interval_seconds,
                "skip_mineru": settings.skip_mineru,
            },
        },
    }


class EmbedRequest(BaseModel):
    texts: list[str] = Field(default_factory=list)
    model: str | None = None


class VectorSearchRequest(BaseModel):
    query: str
    knowledgeBaseIds: list[str] = Field(default_factory=list)
    topK: int = 20
    similarityThreshold: float = 0.6


class HybridSearchRequest(BaseModel):
    query: str
    knowledgeBaseIds: list[str] = Field(default_factory=list)
    topK: int = Field(default=10, ge=1, le=50)
    similarityThreshold: float = Field(default=0.6, ge=0, le=1)
    bm25Weight: float | None = Field(default=None, ge=0)
    vectorWeight: float | None = Field(default=None, ge=0)


class RetrievalProjectionDeleteRequest(BaseModel):
    documentIds: list[str] = Field(default_factory=list)


class BatchReindexApiRequest(BaseModel):
    prefix: str | None = None
    documentIds: list[str] = Field(default_factory=list)
    limit: int | None = Field(default=None, ge=1)
    dryRun: bool = False
    failFast: bool = False


@app.post("/internal/embed")
def embed_texts(request: EmbedRequest) -> dict[str, object]:
    settings = Settings.from_env()
    vectors, dimension = Embedder(settings).embed_texts(request.texts, model=request.model)
    return {
        "code": 0,
        "message": "ok",
        "data": {"vectors": vectors, "dimension": dimension},
    }


@app.post("/internal/search")
def vector_search(request: VectorSearchRequest) -> dict[str, object]:
    settings = Settings.from_env()
    backend_client = BackendClient(settings)
    embedding_config = backend_client.get_embedding_config()
    embedder = Embedder(settings, backend_client)
    vectors, dimension = embedder.embed_texts([request.query], config=embedding_config)
    store = QdrantVectorStore(settings)
    store.ensure_collection(dimension)
    hits = store.search(
        query_vector=vectors[0],
        knowledge_base_ids=request.knowledgeBaseIds,
        top_k=max(1, request.topK),
        similarity_threshold=request.similarityThreshold,
    )
    return {"code": 0, "message": "ok", "data": {"hits": hits}}


@app.post("/internal/search/hybrid")
def hybrid_search(request: HybridSearchRequest) -> dict[str, object]:
    settings = Settings.from_env()
    if not settings.elasticsearch_enabled:
        raise HTTPException(status_code=400, detail="Elasticsearch projection is disabled; set KM_ELASTICSEARCH_ENABLED=true for hybrid search")
    query_vector: list[float] | None = None
    try:
        backend_client = BackendClient(settings)
        embedding_config = backend_client.get_embedding_config()
        embedder = Embedder(settings, backend_client)
        vectors, _dimension = embedder.embed_texts([request.query], config=embedding_config)
        query_vector = vectors[0]
    except Exception as exc:
        logger.warning("Hybrid vector embedding failed; continuing with BM25 branch only: %s", exc)
    qdrant_store = QdrantVectorStore(settings)
    service = HybridSearchService(settings, ElasticsearchChunkStore(settings), qdrant_store)
    try:
        hits = service.search(
            query=request.query,
            query_vector=query_vector,
            knowledge_base_ids=request.knowledgeBaseIds,
            top_k=request.topK,
            similarity_threshold=request.similarityThreshold,
            bm25_weight=request.bm25Weight,
            vector_weight=request.vectorWeight,
        )
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc
    return {"code": 0, "message": "ok", "data": {"hits": hits}}


@app.post("/internal/retrieval-projections:delete")
def delete_retrieval_projections(request: RetrievalProjectionDeleteRequest) -> dict[str, object]:
    settings = Settings.from_env()
    try:
        ElasticsearchChunkStore(settings).delete_by_document_ids(request.documentIds)
        deleted_document_count = len(request.documentIds)
    except Exception as exc:
        logger.warning("Retrieval projection delete failed; continuing, documentIds=%s: %s", request.documentIds, exc)
        deleted_document_count = 0
    return {"code": 0, "message": "ok", "data": {"deletedDocumentCount": deleted_document_count}}


@app.post("/internal/chunks:batch-reindex")
def create_batch_reindex(request: BatchReindexApiRequest) -> dict[str, object]:
    settings = Settings.from_env()
    service = _get_batch_reindex_service(settings)
    job = service.submit(
        BatchReindexRequest(
            prefix=request.prefix,
            document_ids=request.documentIds,
            limit=request.limit,
            dry_run=request.dryRun,
            fail_fast=request.failFast,
        )
    )
    return {"code": 0, "message": "ok", "data": job}


@app.get("/internal/chunks:batch-reindex/{job_id}")
def get_batch_reindex(job_id: str) -> dict[str, object]:
    settings = Settings.from_env()
    service = _get_batch_reindex_service(settings)
    job = service.get(job_id)
    if job is None:
        return {"code": 404, "message": "job not found", "data": None}
    return {"code": 0, "message": "ok", "data": job}
