"""Milvus vector store operations."""
from typing import List, Optional
from pymilvus import Collection, CollectionSchema, DataType, FieldSchema, connections, utility
from app.config import settings

class MilvusClient:
    def __init__(self):
        self.host = settings.milvus_host
        self.port = settings.milvus_port
        self.collection_name = settings.milvus_collection
        self.dimension = settings.milvus_dimension
        self._connected = False

    def _ensure_connection(self):
        if not self._connected:
            connections.connect(host=self.host, port=self.port)
            self._connected = True

    def _ensure_collection(self):
        self._ensure_connection()
        if utility.has_collection(self.collection_name):
            self.collection = Collection(self.collection_name)
            return
        fields = [
            FieldSchema(name="id", dtype=DataType.VARCHAR, is_primary=True, max_length=64),
            FieldSchema(name="chunk_id", dtype=DataType.VARCHAR, max_length=64),
            FieldSchema(name="doc_id", dtype=DataType.VARCHAR, max_length=64),
            FieldSchema(name="kb_id", dtype=DataType.VARCHAR, max_length=64),
            FieldSchema(name="embedding", dtype=DataType.FLOAT_VECTOR, dim=self.dimension),
            FieldSchema(name="content", dtype=DataType.VARCHAR, max_length=65535),
            FieldSchema(name="chapter_path", dtype=DataType.VARCHAR, max_length=512),
        ]
        schema = CollectionSchema(fields, description="KM chunk embeddings")
        self.collection = Collection(self.collection_name, schema)
        index_params = {"index_type": "IVF_FLAT", "metric_type": "IP", "params": {"nlist": 128}}
        self.collection.create_index("embedding", index_params)
        self.collection.load()

    def insert(self, records: List[dict]):
        self._ensure_collection()
        self.collection.insert(records)
        self.collection.flush()

    def search(self, query_vector: List[float], kb_ids: Optional[List[str]] = None, top_k: int = 20, threshold: float = 0.0) -> List[dict]:
        self._ensure_collection()
        self.collection.load()
        expr = None
        if kb_ids:
            ids = [f'"{k}"' for k in kb_ids]
            expr = f"kb_id in [{','.join(ids)}]"
        results = self.collection.search(
            data=[query_vector],
            anns_field="embedding",
            param={"metric_type": "IP", "params": {"nprobe": 10}},
            limit=top_k,
            expr=expr,
            output_fields=["chunk_id", "doc_id", "content", "chapter_path"]
        )
        hits = []
        for hits_batch in results:
            for hit in hits_batch:
                score = hit.score
                if threshold > 0 and score < threshold:
                    continue
                hits.append({
                    "chunk_id": hit.entity.get("chunk_id"),
                    "document_id": hit.entity.get("doc_id"),
                    "doc_id": hit.entity.get("doc_id"),
                    "content": hit.entity.get("content"),
                    "chapter_path": hit.entity.get("chapter_path", ""),
                    "similarity_score": float(score),
                })
        return hits

    def delete_by_doc_id(self, doc_id: str):
        self._ensure_collection()
        self.collection.delete(f'doc_id == "{doc_id}"')

    def delete_by_kb_id(self, kb_id: str):
        self._ensure_collection()
        self.collection.delete(f'kb_id == "{kb_id}"')

    def close(self):
        if self._connected:
            connections.disconnect("default")
            self._connected = False
