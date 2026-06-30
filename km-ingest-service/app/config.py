from __future__ import annotations

from dataclasses import dataclass
import os
from pathlib import Path
import sys
from urllib.parse import urlparse


def _bool_env(name: str, default: bool) -> bool:
    value = os.getenv(name)
    if value is None:
        return default
    return value.strip().lower() in {"1", "true", "yes", "y", "on"}


@dataclass(frozen=True)
class IngestConfig:
    minio_endpoint: str
    minio_access_key: str
    minio_secret_key: str
    minio_bucket: str
    minio_secure: bool
    work_dir: Path
    mineru_backend: str
    mineru_command: str

    @classmethod
    def from_env(cls) -> "IngestConfig":
        service_root = Path(__file__).resolve().parents[1]
        default_mineru = Path(sys.executable).with_name("mineru.exe" if os.name == "nt" else "mineru")
        endpoint, secure_from_endpoint = _normalize_minio_endpoint(os.getenv("MINIO_ENDPOINT", "localhost:9000"))
        return cls(
            minio_endpoint=endpoint,
            minio_access_key=os.getenv("MINIO_ACCESS_KEY", "minioadmin"),
            minio_secret_key=os.getenv("MINIO_SECRET_KEY", "minioadmin"),
            minio_bucket=os.getenv("MINIO_BUCKET", "km-documents"),
            minio_secure=_bool_env("MINIO_SECURE", secure_from_endpoint),
            work_dir=Path(os.getenv("INGEST_WORK_DIR", os.getenv("KM_INGEST_SERVICE_WORK_DIR", str(service_root / ".work")))),
            mineru_backend=os.getenv("MINERU_BACKEND", "pipeline"),
            mineru_command=os.getenv("MINERU_COMMAND", str(default_mineru) if default_mineru.exists() else "mineru"),
        )


def _normalize_minio_endpoint(value: str) -> tuple[str, bool]:
    if "://" not in value:
        return value, False
    parsed = urlparse(value)
    if not parsed.netloc:
        return value, False
    return parsed.netloc, parsed.scheme == "https"
