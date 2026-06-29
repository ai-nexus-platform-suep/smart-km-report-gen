"""MinIO file reader."""
from minio import Minio
from app.config import settings

class MinioReader:
    def __init__(self):
        self.client = Minio(
            endpoint=settings.minio_endpoint.replace("http://", "").replace("https://", ""),
            access_key=settings.minio_access_key,
            secret_key=settings.minio_secret_key,
            secure=settings.minio_endpoint.startswith("https"),
        )
        self.bucket = settings.minio_bucket

    def read_file(self, object_path: str) -> bytes:
        response = self.client.get_object(self.bucket, object_path)
        try:
            return response.read()
        finally:
            response.close()
            response.release_conn()
