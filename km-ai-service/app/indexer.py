from __future__ import annotations

from dataclasses import dataclass
import json
import logging
from typing import Callable

from .backend_client import BackendClient
from .chunker import Chunk, chunk_markdown
from .embedder import Embedder
from .elasticsearch_store import ElasticsearchChunkStore
from .qdrant_store import QdrantVectorStore
from .settings import Settings


logger = logging.getLogger(__name__)


@dataclass(frozen=True)
class DocumentIndexResult:
    chunks: list[Chunk]
    chunk_count: int
    dimension: int | None


class DocumentIndexer:
    def __init__(self, settings: Settings) -> None:
        self._backend_client = BackendClient(settings)
        self._embedder = Embedder(settings, self._backend_client)
        self._qdrant_store = QdrantVectorStore(settings)
        self._elasticsearch_store = ElasticsearchChunkStore(settings)

    def index_markdown(
        self,
        *,
        document_id: str,
        kb_id: str,
        job_id: str | None,
        markdown: str,
        chunk_strategy: str | None,
        source_object: str | None = None,
        metadata_object: str | None = None,
        dry_run: bool = False,
        on_chunks_persisted: Callable[[int], None] | None = None,
    ) -> DocumentIndexResult:
        strategy_config = parse_chunk_strategy(chunk_strategy)
        chunks = chunk_markdown(
            markdown,
            document_id=document_id,
            strategy=strategy_config.get("type"),
            chunk_size=strategy_config.get("chunkSize"),
            overlap=strategy_config.get("overlap"),
        )
        if dry_run:
            return DocumentIndexResult(chunks=chunks, chunk_count=len(chunks), dimension=None)

        chunk_count = self._backend_client.replace_chunks(
            document_id=document_id,
            kb_id=kb_id,
            job_id=job_id,
            chunks=chunks,
        )
        if chunk_count <= 0:
            raise RuntimeError("Chunk persistence returned zero chunks")
        if on_chunks_persisted is not None:
            on_chunks_persisted(chunk_count)

        embedding_config = self._backend_client.get_embedding_config()
        self._qdrant_store.ensure_collection(embedding_config.dimension)
        vectors, dimension = self._embedder.embed_texts([chunk.content for chunk in chunks], config=embedding_config)
        self._qdrant_store.replace_document_vectors(
            document_id=document_id,
            kb_id=kb_id,
            chunks=chunks,
            vectors=vectors,
            source_object=source_object,
            metadata_object=metadata_object,
        )
        try:
            self._elasticsearch_store.replace_document_chunks(document_id=document_id, kb_id=kb_id, chunks=chunks)
        except Exception as exc:
            logger.warning("Elasticsearch chunk projection update failed, document_id=%s: %s", document_id, exc)
        return DocumentIndexResult(chunks=chunks, chunk_count=chunk_count, dimension=dimension)


def parse_chunk_strategy(raw: str | None) -> dict[str, object]:
    if not raw:
        return {"type": "heading"}
    try:
        parsed = json.loads(raw)
    except json.JSONDecodeError:
        return {"type": raw}
    return parsed if isinstance(parsed, dict) else {"type": "heading"}
