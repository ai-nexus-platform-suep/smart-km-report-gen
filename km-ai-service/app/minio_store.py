from __future__ import annotations

import json
from pathlib import Path
from typing import Any

from minio import Minio

from .settings import Settings


class MinioStore:
    def __init__(self, settings: Settings) -> None:
        self._bucket = settings.minio_bucket
        self._client = Minio(
            settings.minio_endpoint,
            access_key=settings.minio_access_key,
            secret_key=settings.minio_secret_key,
            secure=settings.minio_secure,
        )

    def download_file(self, object_name: str, target: Path) -> Path:
        target.parent.mkdir(parents=True, exist_ok=True)
        self._client.fget_object(self._bucket, object_name, str(target))
        return target

    def upload_file(self, object_name: str, path: Path, content_type: str | None = None) -> str:
        self._client.fput_object(self._bucket, object_name, str(path), content_type=content_type or _content_type(path))
        return object_name

    def list_object_names(self, prefix: str) -> list[str]:
        return [item.object_name for item in self._client.list_objects(self._bucket, prefix=prefix, recursive=True)]

    def read_text(self, object_name: str) -> str:
        response = self._client.get_object(self._bucket, object_name)
        try:
            return response.read().decode("utf-8")
        finally:
            response.close()
            response.release_conn()

    def read_json(self, object_name: str) -> dict[str, Any]:
        value = json.loads(self.read_text(object_name))
        if not isinstance(value, dict):
            raise ValueError(f"MinIO object is not a JSON object: {object_name}")
        return value


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
