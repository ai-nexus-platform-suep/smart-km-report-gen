from __future__ import annotations

from concurrent.futures import ThreadPoolExecutor, as_completed
from dataclasses import dataclass, field
from datetime import datetime, timezone
import json
import logging
import uuid

import redis

from .indexer import DocumentIndexer
from .minio_store import MinioStore
from .settings import Settings


logger = logging.getLogger(__name__)
FINAL_STATUSES = {"SUCCEEDED", "PARTIAL_FAILED", "FAILED"}


@dataclass(frozen=True)
class BatchReindexRequest:
    prefix: str | None = None
    document_ids: list[str] = field(default_factory=list)
    limit: int | None = None
    dry_run: bool = False
    fail_fast: bool = False


class BatchReindexService:
    def __init__(self, settings: Settings) -> None:
        self._settings = settings
        self._registry = RedisBatchJobRegistry(settings)
        self._executor = ThreadPoolExecutor(max_workers=settings.batch_reindex_max_active_jobs)

    def submit(self, request: BatchReindexRequest) -> dict[str, object]:
        job = self._registry.create_job(request)
        future = self._executor.submit(_run_job, self._settings, request, job["jobId"])
        future.add_done_callback(_log_future_exception)
        return job

    def get(self, job_id: str) -> dict[str, object] | None:
        return self._registry.get_job(job_id)


class RedisBatchJobRegistry:
    def __init__(self, settings: Settings) -> None:
        self._ttl = settings.batch_reindex_status_ttl_seconds
        self._max_active_jobs = settings.batch_reindex_max_active_jobs
        self._result_limit = settings.batch_reindex_result_limit
        self._client = redis.Redis(
            host=settings.redis_host,
            port=settings.redis_port,
            db=settings.redis_db,
            password=settings.redis_password or None,
            socket_timeout=settings.redis_socket_timeout_seconds,
            decode_responses=True,
        )

    def create_job(self, request: BatchReindexRequest) -> dict[str, object]:
        self._client.ping()
        active_count = self._active_job_count()
        if active_count >= self._max_active_jobs:
            raise RuntimeError("Too many active batch reindex jobs")
        job_id = uuid.uuid4().hex
        now = _now()
        job = {
            "jobId": job_id,
            "status": "QUEUED",
            "filters": _request_payload(request),
            "createdAt": now,
            "updatedAt": now,
            "startedAt": None,
            "finishedAt": None,
            "discovered": 0,
            "processed": 0,
            "succeeded": 0,
            "failed": 0,
            "skipped": 0,
            "results": [],
        }
        self._save(job)
        self._client.sadd(_active_key(), job_id)
        return job

    def get_job(self, job_id: str) -> dict[str, object] | None:
        raw = self._client.get(_job_key(job_id))
        return json.loads(raw) if raw else None

    def mark_running(self, job_id: str) -> None:
        job = self._require_job(job_id)
        job["status"] = "RUNNING"
        job["startedAt"] = job["startedAt"] or _now()
        self._touch(job)

    def set_discovered(self, job_id: str, count: int) -> None:
        job = self._require_job(job_id)
        job["discovered"] = count
        self._touch(job)

    def record_result(self, job_id: str, result: dict[str, object]) -> None:
        job = self._require_job(job_id)
        status = result.get("status")
        job["processed"] = int(job["processed"]) + 1
        if status == "SUCCEEDED":
            job["succeeded"] = int(job["succeeded"]) + 1
        elif status == "SKIPPED":
            job["skipped"] = int(job["skipped"]) + 1
        else:
            job["failed"] = int(job["failed"]) + 1
        results = list(job.get("results") or [])
        if len(results) < self._result_limit:
            results.append(result)
        job["results"] = results
        self._touch(job)

    def finish(self, job_id: str) -> None:
        job = self._require_job(job_id)
        if int(job["failed"]) > 0 and int(job["succeeded"]) == 0 and int(job["skipped"]) == 0:
            status = "FAILED"
        elif int(job["failed"]) > 0:
            status = "PARTIAL_FAILED"
        else:
            status = "SUCCEEDED"
        job["status"] = status
        job["finishedAt"] = _now()
        self._touch(job)
        self._client.srem(_active_key(), job_id)

    def fail_job(self, job_id: str, error: str) -> None:
        job = self._require_job(job_id)
        job["status"] = "FAILED"
        job["finishedAt"] = _now()
        job["results"] = [{"status": "FAILED", "error": error[:1000]}]
        self._touch(job)
        self._client.srem(_active_key(), job_id)

    def _require_job(self, job_id: str) -> dict[str, object]:
        job = self.get_job(job_id)
        if job is None:
            raise RuntimeError(f"Batch reindex job not found: {job_id}")
        return job

    def _touch(self, job: dict[str, object]) -> None:
        job["updatedAt"] = _now()
        self._save(job)

    def _save(self, job: dict[str, object]) -> None:
        self._client.set(_job_key(str(job["jobId"])), json.dumps(job, ensure_ascii=False), ex=self._ttl)

    def _active_job_count(self) -> int:
        count = 0
        for job_id in self._client.smembers(_active_key()):
            job = self.get_job(str(job_id))
            if job is None or str(job.get("status")) in FINAL_STATUSES:
                self._client.srem(_active_key(), job_id)
                continue
            count += 1
        return count


def _run_job(settings: Settings, request: BatchReindexRequest, job_id: str) -> None:
    registry = RedisBatchJobRegistry(settings)
    try:
        registry.mark_running(job_id)
        store = MinioStore(settings)
        artifacts = _discover_artifacts(store, settings, request)
        registry.set_discovered(job_id, len(artifacts))
        if request.fail_fast:
            for artifact in artifacts:
                result = _process_artifact(settings, artifact, request.dry_run, job_id)
                registry.record_result(job_id, result)
                if result.get("status") == "FAILED":
                    break
        else:
            with ThreadPoolExecutor(max_workers=settings.batch_reindex_document_concurrency) as executor:
                futures = [executor.submit(_process_artifact, settings, artifact, request.dry_run, job_id) for artifact in artifacts]
                for future in as_completed(futures):
                    registry.record_result(job_id, future.result())
        registry.finish(job_id)
    except Exception as exc:
        logger.exception("Batch reindex job failed, jobId=%s", job_id)
        registry.fail_job(job_id, str(exc))


def _log_future_exception(future) -> None:
    try:
        exc = future.exception()
    except Exception as callback_exc:
        logger.exception("Batch reindex future callback failed: %s", callback_exc)
        return
    if exc is not None:
        logger.error("Batch reindex future crashed", exc_info=(type(exc), exc, exc.__traceback__))


def _discover_artifacts(store: MinioStore, settings: Settings, request: BatchReindexRequest) -> list[dict[str, object]]:
    prefix = request.prefix or settings.batch_reindex_default_prefix
    document_filter = set(request.document_ids)
    artifacts: list[dict[str, object]] = []
    for object_name in store.list_object_names(prefix):
        if not object_name.endswith("/content.md"):
            continue
        metadata_object = object_name[: -len("content.md")] + "metadata.json"
        try:
            metadata = store.read_json(metadata_object)
        except Exception as exc:
            artifacts.append({"contentObject": object_name, "metadataObject": metadata_object, "metadataError": str(exc)})
            if request.limit is not None and len(artifacts) >= request.limit:
                break
            continue
        document_id = str(metadata.get("documentId") or "")
        if document_filter and document_id not in document_filter:
            continue
        artifacts.append({"contentObject": object_name, "metadataObject": metadata_object, "metadata": metadata})
        if request.limit is not None and len(artifacts) >= request.limit:
            break
    return artifacts


def _process_artifact(settings: Settings, artifact: dict[str, object], dry_run: bool, job_id: str) -> dict[str, object]:
    content_object = str(artifact.get("contentObject") or "")
    metadata_object = str(artifact.get("metadataObject") or "")
    if artifact.get("metadataError"):
        return {"status": "FAILED", "contentObject": content_object, "metadataObject": metadata_object, "error": str(artifact["metadataError"])[:1000]}
    try:
        metadata = artifact.get("metadata")
        if not isinstance(metadata, dict):
            raise ValueError("Metadata is missing")
        document_id = str(metadata.get("documentId") or "").strip()
        kb_id = str(metadata.get("kbId") or "").strip()
        if not document_id or not kb_id:
            raise ValueError("metadata.json must contain documentId and kbId")
        markdown = MinioStore(settings).read_text(content_object)
        result = DocumentIndexer(settings).index_markdown(
            document_id=document_id,
            kb_id=kb_id,
            job_id=job_id,
            markdown=markdown,
            chunk_strategy=str(metadata.get("chunkStrategy") or "") or None,
            source_object=content_object,
            metadata_object=metadata_object,
            dry_run=dry_run,
        )
        status = "SKIPPED" if dry_run else "SUCCEEDED"
        return {
            "status": status,
            "documentId": document_id,
            "kbId": kb_id,
            "contentObject": content_object,
            "metadataObject": metadata_object,
            "chunkCount": result.chunk_count,
            "dimension": result.dimension,
        }
    except Exception as exc:
        return {"status": "FAILED", "contentObject": content_object, "metadataObject": metadata_object, "error": str(exc)[:1000]}


def _request_payload(request: BatchReindexRequest) -> dict[str, object]:
    return {
        "prefix": request.prefix,
        "documentIds": request.document_ids,
        "limit": request.limit,
        "dryRun": request.dry_run,
        "failFast": request.fail_fast,
    }


def _job_key(job_id: str) -> str:
    return f"km:chunk-reindex:job:{job_id}"


def _active_key() -> str:
    return "km:chunk-reindex:active"


def _now() -> str:
    return datetime.now(timezone.utc).isoformat()
