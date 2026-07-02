from __future__ import annotations

import json
from typing import Any

import requests

from .chunker import Chunk
from .settings import Settings


class ElasticsearchChunkStore:
    def __init__(self, settings: Settings) -> None:
        self._enabled = settings.elasticsearch_enabled
        self._base_url = settings.elasticsearch_base_url.rstrip("/")
        self._index = settings.elasticsearch_index_name
        self._timeout = settings.elasticsearch_timeout_seconds
        self._session = requests.Session()
        if settings.elasticsearch_api_key:
            self._session.headers.update({"Authorization": f"ApiKey {settings.elasticsearch_api_key}"})
        elif settings.elasticsearch_username or settings.elasticsearch_password:
            self._session.auth = (settings.elasticsearch_username, settings.elasticsearch_password)

    @property
    def enabled(self) -> bool:
        return self._enabled

    def ensure_index(self) -> None:
        if not self._enabled:
            return
        response = self._request("HEAD", f"/{self._index}", expected=(200, 404))
        if response.status_code == 200:
            return
        mapping = {
            "mappings": {
                "properties": {
                    "chunkId": {"type": "keyword"},
                    "documentId": {"type": "keyword"},
                    "kbId": {"type": "keyword"},
                    "content": {"type": "text"},
                    "chapterPath": {"type": "text", "fields": {"keyword": {"type": "keyword", "ignore_above": 512}}},
                    "chunkIndex": {"type": "integer"},
                    "chunkType": {"type": "keyword"},
                }
            }
        }
        self._request("PUT", f"/{self._index}", json_body=mapping, expected=(200,))

    def replace_document_chunks(self, *, document_id: str, kb_id: str, chunks: list[Chunk]) -> None:
        if not self._enabled:
            return
        self.ensure_index()
        self.delete_by_document_id(document_id)
        if not chunks:
            return
        lines: list[str] = []
        for chunk in chunks:
            lines.append(json.dumps({"index": {"_index": self._index, "_id": chunk.id}}, ensure_ascii=False))
            lines.append(json.dumps(_chunk_document(document_id, kb_id, chunk), ensure_ascii=False))
        response = self._request(
            "POST",
            "/_bulk",
            data="\n".join(lines) + "\n",
            headers={"Content-Type": "application/x-ndjson"},
            expected=(200,),
        )
        body = response.json()
        if body.get("errors"):
            raise RuntimeError("Elasticsearch bulk index reported item errors")

    def delete_by_document_id(self, document_id: str) -> None:
        self.delete_by_document_ids([document_id])

    def delete_by_document_ids(self, document_ids: list[str]) -> None:
        if not self._enabled or not document_ids:
            return
        self.ensure_index()
        query = {"query": {"terms": {"documentId": document_ids}}}
        self._request("POST", f"/{self._index}/_delete_by_query", json_body=query, expected=(200, 404), params={"conflicts": "proceed"})

    def search(self, *, query: str, knowledge_base_ids: list[str], top_k: int) -> list[dict[str, Any]]:
        if not self._enabled:
            raise RuntimeError("Elasticsearch projection is disabled")
        self.ensure_index()
        must: list[dict[str, Any]] = [{"match": {"content": query}}]
        filters: list[dict[str, Any]] = []
        if knowledge_base_ids:
            filters.append({"terms": {"kbId": knowledge_base_ids}})
        body = {
            "size": max(1, top_k),
            "query": {"bool": {"must": must, "filter": filters}},
            "_source": ["chunkId", "documentId", "kbId", "content", "chapterPath", "chunkIndex", "chunkType"],
        }
        response = self._request("POST", f"/{self._index}/_search", json_body=body, expected=(200,))
        hits = response.json().get("hits", {}).get("hits", [])
        results: list[dict[str, Any]] = []
        for hit in hits:
            source = hit.get("_source") or {}
            results.append(
                {
                    "chunkId": source.get("chunkId") or hit.get("_id"),
                    "documentId": source.get("documentId"),
                    "content": source.get("content"),
                    "chapterPath": source.get("chapterPath"),
                    "chunkIndex": source.get("chunkIndex"),
                    "chunkType": source.get("chunkType"),
                    "bm25Score": float(hit.get("_score") or 0),
                }
            )
        return results

    def _request(
        self,
        method: str,
        path: str,
        *,
        json_body: dict[str, Any] | None = None,
        data: str | None = None,
        headers: dict[str, str] | None = None,
        params: dict[str, str] | None = None,
        expected: tuple[int, ...],
    ) -> requests.Response:
        response = self._session.request(
            method,
            self._base_url + path,
            json=json_body,
            data=data,
            headers=headers,
            params=params,
            timeout=self._timeout,
        )
        if response.status_code not in expected:
            raise RuntimeError(f"Elasticsearch request failed: method={method}, path={path}, status={response.status_code}")
        return response


def _chunk_document(document_id: str, kb_id: str, chunk: Chunk) -> dict[str, Any]:
    return {
        "chunkId": chunk.id,
        "documentId": document_id,
        "kbId": kb_id,
        "content": chunk.content,
        "chapterPath": chunk.chapter_path,
        "chunkIndex": chunk.chunk_index,
        "chunkType": chunk.chunk_type,
    }
