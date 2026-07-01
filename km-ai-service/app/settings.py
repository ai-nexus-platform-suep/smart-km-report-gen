from __future__ import annotations

from dataclasses import dataclass
import os
from pathlib import Path
import sys
from urllib.parse import urlparse

from dotenv import load_dotenv


SERVICE_ROOT = Path(__file__).resolve().parents[1]
load_dotenv(SERVICE_ROOT / ".env")


def _bool_env(name: str, default: bool) -> bool:
    value = os.getenv(name)
    if value is None:
        return default
    return value.strip().lower() in {"1", "true", "yes", "y", "on"}


def _int_env(name: str, default: int) -> int:
    value = os.getenv(name)
    if value is None or not value.strip():
        return default
    try:
        return max(1, int(value))
    except ValueError:
        return default


def _normalize_minio_endpoint(value: str) -> tuple[str, bool]:
    if "://" not in value:
        return value, False
    parsed = urlparse(value)
    if not parsed.netloc:
        return value, False
    return parsed.netloc, parsed.scheme == "https"


@dataclass(frozen=True)
class Settings:
    rabbitmq_host: str
    rabbitmq_port: int
    rabbitmq_username: str
    rabbitmq_password: str
    rabbitmq_virtual_host: str
    rabbitmq_exchange: str
    rabbitmq_queue: str
    rabbitmq_routing_key: str
    rabbitmq_dead_letter_exchange: str
    rabbitmq_dead_letter_queue: str
    rabbitmq_dead_letter_routing_key: str
    rabbitmq_prefetch_count: int
    max_concurrent_parse_jobs: int
    backend_status_url: str
    minio_endpoint: str
    minio_access_key: str
    minio_secret_key: str
    minio_bucket: str
    minio_secure: bool
    work_dir: Path
    mineru_backend: str
    mineru_command: str
    mineru_timeout_seconds: int
    mineru_enable_formula: bool
    mineru_enable_table: bool
    mineru_enable_image_analysis: bool
    skip_mineru: bool

    @classmethod
    def from_env(cls) -> "Settings":
        default_mineru = Path(sys.executable).with_name("mineru.exe" if os.name == "nt" else "mineru")
        endpoint, secure_from_endpoint = _normalize_minio_endpoint(os.getenv("MINIO_ENDPOINT", "localhost:9000"))
        return cls(
            rabbitmq_host=os.getenv("RABBITMQ_HOST", "localhost"),
            rabbitmq_port=_int_env("RABBITMQ_PORT", 5672),
            rabbitmq_username=os.getenv("RABBITMQ_USER", os.getenv("RABBITMQ_USERNAME", "km")),
            rabbitmq_password=os.getenv("RABBITMQ_PASSWORD", "km123456"),
            rabbitmq_virtual_host=os.getenv("RABBITMQ_VIRTUAL_HOST", "/"),
            rabbitmq_exchange=os.getenv("DOCUMENT_PROCESSING_EXCHANGE", "km.document.processing"),
            rabbitmq_queue=os.getenv("DOCUMENT_PROCESSING_QUEUE", "km.document.processing.parse"),
            rabbitmq_routing_key=os.getenv("DOCUMENT_PROCESSING_ROUTING_KEY", "document.process"),
            rabbitmq_dead_letter_exchange=os.getenv("DOCUMENT_PROCESSING_DLX", "km.document.processing.dlx"),
            rabbitmq_dead_letter_queue=os.getenv("DOCUMENT_PROCESSING_DLQ", "km.document.processing.dlq"),
            rabbitmq_dead_letter_routing_key=os.getenv("DOCUMENT_PROCESSING_DLQ_ROUTING_KEY", "document.process.dead"),
            rabbitmq_prefetch_count=_int_env("RABBITMQ_PREFETCH_COUNT", 1),
            max_concurrent_parse_jobs=_int_env("MAX_CONCURRENT_PARSE_JOBS", 1),
            backend_status_url=os.getenv("KM_BACKEND_STATUS_URL", "http://localhost:8091/internal/documents/status"),
            minio_endpoint=endpoint,
            minio_access_key=os.getenv("MINIO_ACCESS_KEY", "minioadmin"),
            minio_secret_key=os.getenv("MINIO_SECRET_KEY", "minioadmin"),
            minio_bucket=os.getenv("MINIO_BUCKET", "km-documents"),
            minio_secure=_bool_env("MINIO_SECURE", secure_from_endpoint),
            work_dir=Path(os.getenv("KM_AI_SERVICE_WORK_DIR", str(SERVICE_ROOT / ".work"))),
            mineru_backend=os.getenv("MINERU_BACKEND", "pipeline"),
            mineru_command=os.getenv("MINERU_COMMAND", str(default_mineru) if default_mineru.exists() else "mineru"),
            mineru_timeout_seconds=_int_env("MINERU_TIMEOUT_SECONDS", 600),
            mineru_enable_formula=_bool_env("MINERU_ENABLE_FORMULA", False),
            mineru_enable_table=_bool_env("MINERU_ENABLE_TABLE", True),
            mineru_enable_image_analysis=_bool_env("MINERU_ENABLE_IMAGE_ANALYSIS", False),
            skip_mineru=_bool_env("KM_AI_SKIP_MINERU", False),
        )
