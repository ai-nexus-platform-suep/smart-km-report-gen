"""Application configuration via environment variables."""
from pydantic_settings import BaseSettings


class Settings(BaseSettings):
    # Milvus
    milvus_host: str = "localhost"
    milvus_port: int = 19530
    milvus_collection: str = "km_chunks"
    milvus_dimension: int = 1024

    # RabbitMQ
    rabbitmq_host: str = "localhost"
    rabbitmq_port: int = 5672
    rabbitmq_user: str = "km"
    rabbitmq_password: str = "km123456"
    rabbitmq_queue: str = "km.document.process"

    # MinIO
    minio_endpoint: str = "http://localhost:9000"
    minio_access_key: str = "minioadmin"
    minio_secret_key: str = "minioadmin"
    minio_bucket: str = "km-documents"

    # SiliconFlow (Embedding & Rerank)
    siliconflow_api_key: str = ""
    siliconflow_base_url: str = "https://api.siliconflow.cn/v1"
    embed_model: str = "BAAI/bge-m3"
    rerank_model: str = "BAAI/bge-reranker-v2-m3"

    # Java Backend Callback
    java_callback_base_url: str = "http://localhost:8091"

    # Parser config (concurrency)
    max_concurrent_tasks: int = 3
    # Embedding batch
    embed_batch_size: int = 32

    # Retry
    task_retry_max: int = 3
    task_retry_base_delay: float = 2.0

    # Hybrid search weights
    hybrid_vector_weight: float = 0.7
    hybrid_keyword_weight: float = 0.3


    model_config = {"env_file": ".env", "env_file_encoding": "utf-8", "extra": "ignore"}


settings = Settings()
