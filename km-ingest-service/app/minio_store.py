from __future__ import annotations

from pathlib import Path

from minio import Minio
from minio.error import S3Error

from .config import IngestConfig


class MinioStore:
    def __init__(self, config: IngestConfig) -> None:
        self.bucket = config.minio_bucket
        self.client = Minio(
            config.minio_endpoint,
            access_key=config.minio_access_key,
            secret_key=config.minio_secret_key,
            secure=config.minio_secure,
        )

    def ensure_bucket(self) -> None:
        try:
            if not self.client.bucket_exists(self.bucket):
                self.client.make_bucket(self.bucket)
        except S3Error as exc:
            raise RuntimeError(
                f"MinIO bucket check failed: {self.bucket}. "
                "Check MINIO_ENDPOINT uses the S3 API port and MINIO_ACCESS_KEY/MINIO_SECRET_KEY match this MinIO server."
            ) from exc

    def upload_file(self, object_name: str, path: Path, content_type: str | None = None) -> str:
        self.ensure_bucket()
        try:
            self.client.fput_object(
                self.bucket,
                object_name,
                str(path),
                content_type=content_type or _content_type(path),
            )
        except S3Error as exc:
            raise RuntimeError(f"MinIO upload failed: {object_name}") from exc
        return object_name


def _content_type(path: Path) -> str:
    suffix = path.suffix.lower()
    if suffix == ".pdf":
        return "application/pdf"
    if suffix == ".md":
        return "text/markdown"
    if suffix == ".json":
        return "application/json"
    if suffix == ".png":
        return "image/png"
    if suffix in {".jpg", ".jpeg"}:
        return "image/jpeg"
    if suffix == ".webp":
        return "image/webp"
    return "application/octet-stream"
