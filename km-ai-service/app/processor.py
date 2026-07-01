from __future__ import annotations

from dataclasses import dataclass, replace
from datetime import datetime, timezone
from pathlib import Path
import json
import logging
import re
import shutil

from .artifacts import ParsedArtifacts, collect_artifacts
from .mineru_agent_client import MineruAgentClient, MineruAgentParseResult
from .minio_store import MinioStore
from .settings import Settings
from .status_client import StatusClient


logger = logging.getLogger(__name__)
SAFE_NAME_PATTERN = re.compile(r"[^\w._-]+", re.UNICODE)


@dataclass(frozen=True)
class DocumentProcessJob:
    job_id: str
    document_id: str
    kb_id: str
    kb_name: str | None
    raw_object: str
    filename: str
    mime_type: str | None
    parser_backend: str | None
    chunk_strategy: str | None
    attempt: int
    callback_url: str | None

    @classmethod
    def from_message(cls, message: dict[str, object]) -> "DocumentProcessJob":
        return cls(
            job_id=str(message.get("jobId") or ""),
            document_id=str(message["documentId"]),
            kb_id=str(message["kbId"]),
            kb_name=str(message.get("kbName") or "") or None,
            raw_object=str(message["rawObject"]),
            filename=str(message.get("filename") or "source"),
            mime_type=str(message.get("mimeType") or "") or None,
            parser_backend=str(message.get("parserBackend") or "") or None,
            chunk_strategy=str(message.get("chunkStrategy") or "") or None,
            attempt=int(message.get("attempt") or 1),
            callback_url=str(message.get("callbackUrl") or "") or None,
        )


class DocumentProcessor:
    def __init__(self, settings: Settings) -> None:
        self._settings = settings
        self._store = MinioStore(settings)
        self._mineru_agent = MineruAgentClient(settings)
        self._status_client = StatusClient(settings)

    def process(self, job: DocumentProcessJob) -> None:
        logger.info("Document processing started, docId=%s, jobId=%s", job.document_id, job.job_id)
        self._status_client.update(
            document_id=job.document_id,
            status="PARSING",
            callback_url=job.callback_url,
            job_id=job.job_id,
            attempt=job.attempt,
        )

        work_dir = self._settings.work_dir / job.document_id
        if work_dir.exists():
            shutil.rmtree(work_dir)
        input_path = work_dir / "input" / _source_name(job)
        mineru_output_dir = work_dir / "mineru-output"
        normalized_dir = work_dir / "normalized"

        try:
            if self._settings.skip_mineru:
                self._store.download_file(job.raw_object, input_path)
                logger.info("MinerU skipped by configuration, docId=%s", job.document_id)
            else:
                self._store.download_file(job.raw_object, input_path)
                parse_result = self._mineru_agent.parse_file(
                    source_path=input_path,
                    file_name=job.filename,
                    output_dir=mineru_output_dir,
                )
                artifacts = collect_artifacts(mineru_output_dir, normalized_dir)
                if artifacts.markdown is None:
                    raise RuntimeError("MinerU Agent parse completed but normalized content.md was not created")
                artifacts = replace(artifacts, metadata_json=self._write_metadata(job, normalized_dir, parse_result))
                self._upload_artifacts(job, artifacts)

            self._status_client.update(
                document_id=job.document_id,
                status="READY",
                callback_url=job.callback_url,
                job_id=job.job_id,
                attempt=job.attempt,
                chunk_count=0,
            )
            logger.info("Document processing finished, docId=%s, jobId=%s", job.document_id, job.job_id)
        except Exception as exc:
            logger.exception("Document processing failed, docId=%s, jobId=%s", job.document_id, job.job_id)
            self._status_client.update(
                document_id=job.document_id,
                status="FAILED",
                callback_url=job.callback_url,
                job_id=job.job_id,
                attempt=job.attempt,
                error_msg=str(exc),
            )
            raise

    def _upload_artifacts(self, job: DocumentProcessJob, artifacts: ParsedArtifacts) -> None:
        artifact_prefix = _artifact_prefix(job)
        if artifacts.markdown is not None:
            self._store.upload_file(f"{artifact_prefix}/content.md", artifacts.markdown)
        if artifacts.middle_json is not None:
            self._store.upload_file(f"{artifact_prefix}/middle.json", artifacts.middle_json)
        if artifacts.layout_json is not None:
            self._store.upload_file(f"{artifact_prefix}/layout.json", artifacts.layout_json)
        if artifacts.metadata_json is not None:
            self._store.upload_file(f"{artifact_prefix}/metadata.json", artifacts.metadata_json)

    def _write_metadata(self, job: DocumentProcessJob, normalized_dir: Path, parse_result: MineruAgentParseResult) -> Path:
        artifact_prefix = _artifact_prefix(job)
        content_object = f"{artifact_prefix}/content.md"
        metadata_object = f"{artifact_prefix}/metadata.json"
        metadata_path = normalized_dir / "metadata.json"
        metadata_path.parent.mkdir(parents=True, exist_ok=True)
        metadata = {
            "documentId": job.document_id,
            "jobId": job.job_id,
            "kbId": job.kb_id,
            "kbName": job.kb_name,
            "filename": job.filename,
            "mimeType": job.mime_type,
            "rawObject": job.raw_object,
            "contentObject": content_object,
            "metadataObject": metadata_object,
            "parserBackend": "mineru-agent-file",
            "chunkStrategy": job.chunk_strategy,
            "artifactPrefix": artifact_prefix,
            "mineruAgent": {
                "taskId": parse_result.task_id,
                "state": parse_result.state,
                "apiBaseUrl": self._settings.mineru_agent_api_base_url,
            },
            "parseOptions": self._mineru_agent.parse_options,
            "createdAt": datetime.now(timezone.utc).isoformat(),
        }
        metadata_path.write_text(json.dumps(metadata, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        return metadata_path


def _source_name(job: DocumentProcessJob) -> str:
    suffix = Path(job.filename).suffix or Path(job.raw_object).suffix or ".bin"
    return f"source{suffix.lower()}"


def _artifact_prefix(job: DocumentProcessJob) -> str:
    return f"parsed/{_kb_segment(job)}/{_safe_stem(job.filename)}--doc-{job.document_id}"


def _kb_segment(job: DocumentProcessJob) -> str:
    kb_name = _safe_segment(job.kb_name or "kb")
    return f"kb-{kb_name}--{_short_identifier(job.kb_id)}"


def _safe_stem(filename: str) -> str:
    stem = Path(filename or "document").stem.strip()
    return _safe_segment(stem or "document")


def _safe_segment(value: str) -> str:
    safe = SAFE_NAME_PATTERN.sub("-", value.strip()).strip(".-_")
    return safe[:80] if safe else "unknown"


def _short_identifier(value: str) -> str:
    safe = _safe_segment(value)
    return safe[:8]
