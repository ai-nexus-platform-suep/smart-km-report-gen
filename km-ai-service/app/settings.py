from __future__ import annotations

from dataclasses import dataclass
import os
from pathlib import Path
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


def _non_negative_int_env(name: str, default: int) -> int:
    value = os.getenv(name)
    if value is None or not value.strip():
        return default
    try:
        return max(0, int(value))
    except ValueError:
        return default


def _float_env(name: str, default: float) -> float:
    value = os.getenv(name)
    if value is None or not value.strip():
        return default
    try:
        return float(value)
    except ValueError:
        return default


def _str_env(name: str, default: str) -> str:
    value = os.getenv(name)
    if value is None or not value.strip():
        return default
    return value.strip()


def _choice_env(name: str, default: str, choices: set[str]) -> str:
    value = _str_env(name, default).lower()
    return value if value in choices else default


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
    backend_base_url: str
    backend_embedding_config_url: str
    backend_replace_chunks_url_template: str
    embedding_config_source: str
    embedding_model_name: str
    embedding_api_url: str
    embedding_api_key: str
    embedding_dimension: int
    embedding_batch_size: int
    embedding_request_timeout_seconds: int
    redis_host: str
    redis_port: int
    redis_db: int
    redis_password: str
    redis_socket_timeout_seconds: int
    batch_reindex_status_ttl_seconds: int
    batch_reindex_max_active_jobs: int
    batch_reindex_document_concurrency: int
    batch_reindex_result_limit: int
    batch_reindex_default_prefix: str
    qdrant_url: str
    qdrant_api_key: str
    qdrant_collection: str
    qdrant_timeout_seconds: int
    elasticsearch_enabled: bool
    elasticsearch_base_url: str
    elasticsearch_index_name: str
    elasticsearch_username: str
    elasticsearch_password: str
    elasticsearch_api_key: str
    elasticsearch_timeout_seconds: int
    hybrid_bm25_weight: float
    hybrid_vector_weight: float
    hybrid_candidate_multiplier: int
    minio_endpoint: str
    minio_access_key: str
    minio_secret_key: str
    minio_bucket: str
    minio_secure: bool
    work_dir: Path
    mineru_backend: str
    mineru_method: str
    mineru_lang: str
    mineru_agent_api_base_url: str
    mineru_paid_api_base_url: str
    mineru_api_token: str
    mineru_model_version: str
    mineru_page_ranges: str
    mineru_timeout_seconds: int
    mineru_poll_interval_seconds: int
    mineru_enable_formula: bool
    mineru_enable_table: bool
    skip_mineru: bool

    @classmethod
    def from_env(cls) -> "Settings":
        endpoint, secure_from_endpoint = _normalize_minio_endpoint(os.getenv("MINIO_ENDPOINT", "localhost:9000"))
        minio_secure = _bool_env("MINIO_SECURE", secure_from_endpoint)
        mineru_timeout_seconds = _int_env("MINERU_TIMEOUT_SECONDS", 600)
        backend_status_url = os.getenv("KM_BACKEND_STATUS_URL", "http://localhost:8091/internal/documents/status")
        backend_base_url = _str_env("KM_BACKEND_BASE_URL", _backend_base_from_status_url(backend_status_url))
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
            backend_status_url=backend_status_url,
            backend_base_url=backend_base_url.rstrip("/"),
            backend_embedding_config_url=_str_env(
                "KM_BACKEND_EMBEDDING_CONFIG_URL",
                backend_base_url.rstrip("/") + "/internal/config/embedding",
            ),
            backend_replace_chunks_url_template=_str_env(
                "KM_BACKEND_REPLACE_CHUNKS_URL_TEMPLATE",
                backend_base_url.rstrip("/") + "/internal/documents/{document_id}/chunks:replace",
            ),
            embedding_config_source=_choice_env("KM_AI_EMBEDDING_CONFIG_SOURCE", "backend", {"backend", "env"}),
            embedding_model_name=_str_env("KM_AI_EMBEDDING_MODEL_NAME", ""),
            embedding_api_url=_str_env("KM_AI_EMBEDDING_API_URL", ""),
            embedding_api_key=_str_env("KM_AI_EMBEDDING_API_KEY", _str_env("DASHSCOPE_API_KEY", "")),
            embedding_dimension=_int_env("KM_AI_EMBEDDING_DIMENSION", 0),
            embedding_batch_size=_int_env("KM_AI_EMBEDDING_BATCH_SIZE", 10),
            embedding_request_timeout_seconds=_int_env("KM_AI_EMBEDDING_TIMEOUT_SECONDS", 60),
            redis_host=_str_env("REDIS_HOST", "localhost"),
            redis_port=_int_env("REDIS_PORT", 6379),
            redis_db=_non_negative_int_env("REDIS_DB", 0),
            redis_password=_str_env("REDIS_PASSWORD", ""),
            redis_socket_timeout_seconds=_int_env("REDIS_SOCKET_TIMEOUT_SECONDS", 5),
            batch_reindex_status_ttl_seconds=_int_env("KM_AI_BATCH_REINDEX_STATUS_TTL_SECONDS", 86400),
            batch_reindex_max_active_jobs=_int_env("KM_AI_BATCH_REINDEX_MAX_ACTIVE_JOBS", 1),
            batch_reindex_document_concurrency=_int_env("KM_AI_BATCH_REINDEX_DOCUMENT_CONCURRENCY", 2),
            batch_reindex_result_limit=_int_env("KM_AI_BATCH_REINDEX_RESULT_LIMIT", 200),
            batch_reindex_default_prefix=_str_env("KM_AI_BATCH_REINDEX_PREFIX", "parsed/"),
            qdrant_url=_str_env("QDRANT_URL", "http://localhost:6333"),
            qdrant_api_key=_str_env("QDRANT_API_KEY", ""),
            qdrant_collection=_str_env("QDRANT_COLLECTION", "km_chunks"),
            qdrant_timeout_seconds=_int_env("QDRANT_TIMEOUT_SECONDS", 30),
            elasticsearch_enabled=_bool_env("KM_ELASTICSEARCH_ENABLED", False),
            elasticsearch_base_url=_str_env("KM_ELASTICSEARCH_BASE_URL", "http://localhost:9200").rstrip("/"),
            elasticsearch_index_name=_str_env("KM_ELASTICSEARCH_INDEX_NAME", "km_chunks_bm25"),
            elasticsearch_username=_str_env("KM_ELASTICSEARCH_USERNAME", ""),
            elasticsearch_password=_str_env("KM_ELASTICSEARCH_PASSWORD", ""),
            elasticsearch_api_key=_str_env("KM_ELASTICSEARCH_API_KEY", ""),
            elasticsearch_timeout_seconds=_int_env("KM_ELASTICSEARCH_TIMEOUT_SECONDS", 5),
            hybrid_bm25_weight=_float_env("KM_SEARCH_HYBRID_BM25_WEIGHT", 0.45),
            hybrid_vector_weight=_float_env("KM_SEARCH_HYBRID_VECTOR_WEIGHT", 0.55),
            hybrid_candidate_multiplier=_int_env("KM_SEARCH_HYBRID_CANDIDATE_MULTIPLIER", 3),
            minio_endpoint=endpoint,
            minio_access_key=os.getenv("MINIO_ACCESS_KEY", "minioadmin"),
            minio_secret_key=os.getenv("MINIO_SECRET_KEY", "minioadmin"),
            minio_bucket=os.getenv("MINIO_BUCKET", "km-documents"),
            minio_secure=minio_secure,
            work_dir=Path(os.getenv("KM_AI_SERVICE_WORK_DIR", str(SERVICE_ROOT / ".work"))),
            mineru_backend=_choice_env("MINERU_BACKEND", "agent", {"agent", "paid_v4"}),
            mineru_method=_choice_env("MINERU_METHOD", "auto", {"auto", "txt", "ocr"}),
            mineru_lang=_str_env("MINERU_LANG", "ch"),
            mineru_agent_api_base_url=_str_env("MINERU_AGENT_API_BASE_URL", "https://mineru.net/api/v1/agent"),
            mineru_paid_api_base_url=_str_env("MINERU_PAID_API_BASE_URL", "https://mineru.net/api/v4"),
            mineru_api_token=_str_env("MINERU_API_TOKEN", ""),
            mineru_model_version=_str_env("MINERU_MODEL_VERSION", "vlm"),
            mineru_page_ranges=_str_env("MINERU_PAGE_RANGES", ""),
            mineru_timeout_seconds=mineru_timeout_seconds,
            mineru_poll_interval_seconds=_int_env("MINERU_POLL_INTERVAL_SECONDS", 2),
            mineru_enable_formula=_bool_env("MINERU_ENABLE_FORMULA", False),
            mineru_enable_table=_bool_env("MINERU_ENABLE_TABLE", True),
            skip_mineru=_bool_env("KM_AI_SKIP_MINERU", False),
        )


def _backend_base_from_status_url(status_url: str) -> str:
    suffix = "/internal/documents/status"
    value = status_url.rstrip("/")
    if value.endswith(suffix):
        return value[: -len(suffix)]
    return "http://localhost:8091"
