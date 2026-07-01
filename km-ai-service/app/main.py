from __future__ import annotations

from fastapi import FastAPI

from .settings import Settings


app = FastAPI(title="km-ai-service", version="0.1.0")


@app.get("/internal/health")
def health() -> dict[str, object]:
    settings = Settings.from_env()
    return {
        "code": 0,
        "message": "ok",
        "data": {
            "service": "km-ai-service",
            "status": "UP",
            "queue": settings.rabbitmq_queue,
            "max_concurrent_parse_jobs": settings.max_concurrent_parse_jobs,
            "rabbitmq_prefetch_count": settings.rabbitmq_prefetch_count,
        },
    }
