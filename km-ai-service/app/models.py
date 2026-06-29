"""Pydantic request/response models for the AI service."""
from __future__ import annotations

from typing import Any, Dict, List, Optional

from pydantic import BaseModel, Field


class ApiResponse(BaseModel):
    code: int = 0
    message: str = "ok"
    data: Optional[Any] = None

    @classmethod
    def ok(cls, data: Any = None) -> "ApiResponse":
        return cls(code=0, message="ok", data=data)

    @classmethod
    def fail(cls, message: str, code: int = 500) -> "ApiResponse":
        return cls(code=code, message=message, data=None)


class ChunkStrategyConfig(BaseModel):
    type: str = Field(default="heading", pattern=r"^(heading|fixed_size)$")
    chunk_size: int = 512
    overlap: int = 50
    separator: str = "\\n\\n"
    recursive_merge: bool = False


class ProcessDocumentRequest(BaseModel):
    document_id: str
    kb_id: str
    minio_path: str
    mime_type: str = "application/octet-stream"
    chunk_strategy: ChunkStrategyConfig
    callback_url: str = ""


class ProcessDocumentResult(BaseModel):
    document_id: str
    chunk_count: int = 0
    status: str = "READY"
    error_msg: str = ""


class EmbedRequest(BaseModel):
    texts: List[str]
    model: str = ""


class EmbedResult(BaseModel):
    vectors: List[List[float]]
    dimension: int


class RerankRequest(BaseModel):
    query: str
    passages: List[str]
    top_k: int = 10
    model: str = ""


class RerankItem(BaseModel):
    index: int
    score: float


class RerankResult(BaseModel):
    items: List[RerankItem]


class VectorSearchRequest(BaseModel):
    query: str
    knowledge_base_ids: List[str] = Field(default_factory=list)
    top_k: int = 20
    similarity_threshold: float = 0.6
    embed_model: str = ""


class VectorSearchHit(BaseModel):
    chunk_id: str
    document_id: str
    content: str
    chapter_path: str = ""
    similarity_score: float = 0.0


class VectorSearchResult(BaseModel):
    hits: List[VectorSearchHit] = Field(default_factory=list)


class ChunkRecord(BaseModel):
    id: str
    doc_id: str
    content: str
    chapter_path: str = ""
    chunk_index: int = 0
    chunk_type: str = "paragraph"
    char_count: int = 0
    vector_id: str = ""


class StatusCallbackRequest(BaseModel):
    document_id: str
    status: str
    error_msg: str = ""
    chunks: List[ChunkRecord] = Field(default_factory=list)
