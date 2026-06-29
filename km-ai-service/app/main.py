"""km-ai-service: FastAPI application for document processing pipeline.
Chunk, embed, rerank, and vector search - all in Python.
"""
import logging
from contextlib import asynccontextmanager
from typing import List

from fastapi import FastAPI, HTTPException

from app.config import settings
from app.models import (
    ApiResponse, ProcessDocumentRequest, ProcessDocumentResult,
    EmbedRequest, EmbedResult, RerankRequest, RerankResult,
    RerankItem, VectorSearchRequest, VectorSearchResult, VectorSearchHit,
)
from app.embedder import Embedder
from app.reranker import Reranker
from app.milvus_client import MilvusClient
from app.minio_client import MinioReader
from app.parser import DocumentParser
from app.chunker import create_chunker
from app.consumer import DocumentConsumer

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s: %(message)s")
logger = logging.getLogger(__name__)

# Singleton instances
embedder = Embedder()
reranker = Reranker()
milvus = MilvusClient()
minio_reader = MinioReader()
parser = DocumentParser()
consumer = DocumentConsumer()


@asynccontextmanager
async def lifespan(app: FastAPI):
    logger.info("km-ai-service starting up...")
    consumer.start()
    yield
    consumer.stop()
    milvus.close()
    logger.info("km-ai-service shut down.")


app = FastAPI(title="km-ai-service", version="1.0.0", lifespan=lifespan)


# ── Health ──
@app.get("/internal/health")
def health():
    return ApiResponse.ok({"service": "km-ai-service", "status": "UP"})


# ── Process document (sync variant) ──
@app.post("/internal/process/document", response_model=ApiResponse)
def process_document(req: ProcessDocumentRequest):
    try:
        file_content = minio_reader.read_file(req.minio_path)
        text = parser.parse(file_content, req.mime_type, req.minio_path)
        if not text.strip():
            raise ValueError("Empty document after parsing")
        chunker = create_chunker(req.chunk_strategy.model_dump())
        chunk_results = chunker.chunk(text)
        texts = [c.content for c in chunk_results]
        vectors = embedder.embed(texts)
        import uuid
        milvus_records = []
        for i, (cr, vec) in enumerate(zip(chunk_results, vectors)):
            chunk_uuid = str(uuid.uuid4())
            vector_uuid = str(uuid.uuid4())
            milvus_records.append({
                "id": vector_uuid, "chunk_id": chunk_uuid, "doc_id": req.document_id,
                "kb_id": req.kb_id, "embedding": vec, "content": cr.content,
                "chapter_path": cr.chapter_path,
            })
        milvus.insert(milvus_records)
        return ApiResponse.ok(ProcessDocumentResult(
            document_id=req.document_id, chunk_count=len(chunk_results), status="READY"
        ))
    except Exception as e:
        logger.error("process_document failed: %s", e)
        return ApiResponse.ok(ProcessDocumentResult(
            document_id=req.document_id, status="FAILED", error_msg=str(e)
        ))


# ── Embed ──
@app.post("/internal/embed", response_model=ApiResponse)
def embed_texts(req: EmbedRequest):
    try:
        vectors = embedder.embed(req.texts, req.model)
        dim = len(vectors[0]) if vectors else settings.milvus_dimension
        return ApiResponse.ok(EmbedResult(vectors=vectors, dimension=dim))
    except Exception as e:
        logger.error("embed failed: %s", e)
        return ApiResponse.fail(str(e))


# ── Rerank ──
@app.post("/internal/rerank", response_model=ApiResponse)
def rerank_passages(req: RerankRequest):
    try:
        results = reranker.rerank(req.query, req.passages, req.top_k, req.model)
        items = [RerankItem(index=r["index"], score=r["score"]) for r in results]
        return ApiResponse.ok(RerankResult(items=items))
    except Exception as e:
        logger.error("rerank failed: %s", e)
        return ApiResponse.fail(str(e))


# ── Vector search ──
@app.post("/internal/search", response_model=ApiResponse)
def vector_search(req: VectorSearchRequest):
    try:
        # 1. Vector search
        query_vec = embedder.embed([req.query], req.embed_model)
        vector_hits = []
        if query_vec:
            vector_hits = milvus.search(
                query_vector=query_vec[0],
                kb_ids=req.knowledge_base_ids if req.knowledge_base_ids else None,
                top_k=req.top_k * 2,
                threshold=req.similarity_threshold * 0.5,  # lower threshold to get more candidates
            )

        # 2. Hybrid: fuse with keyword BM25-style scoring
        from collections import Counter
        query_terms = set(req.query.lower().split())
        for hit in vector_hits:
            content = hit.get("content", "").lower()
            # Simple BM25-like term frequency scoring
            score = 0.0
            content_words = content.split()
            word_count = len(content_words)
            if word_count > 0:
                term_freq = sum(1 for w in content_words if w in query_terms)
                score = term_freq / max(word_count, 1) * 100  # normalize
            hit["keyword_score"] = round(score, 4)

        # Fuse vector + keyword scores
        settings_local = _get_settings()
        vw = getattr(settings_local, "hybrid_vector_weight", 0.7)
        kw = getattr(settings_local, "hybrid_keyword_weight", 0.3)
        for hit in vector_hits:
            vec_score = hit.get("similarity_score", 0.0)
            kw_score = hit.get("keyword_score", 0.0) / 100.0  # normalize
            hit["hybrid_score"] = round(vw * vec_score + kw * kw_score, 4)

        # Sort by hybrid score
        vector_hits.sort(key=lambda h: h.get("hybrid_score", 0), reverse=True)

        result_hits = [VectorSearchHit(
            chunk_id=h["chunk_id"], document_id=h.get("document_id", h.get("doc_id", "")),
            content=h["content"], chapter_path=h.get("chapter_path", ""),
            similarity_score=h["similarity_score"]
        ) for h in vector_hits[:req.top_k]]

        return ApiResponse.ok(VectorSearchResult(hits=result_hits))
    except Exception as e:
        logger.error("search failed: %s", e)
        return ApiResponse.fail(str(e))


def _get_settings():
    """Helper to access settings without circular imports."""
    from app.config import settings
    return settings

