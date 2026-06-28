"""调用 Java 服务拉取配置 (人员 A 独占)"""

import httpx

from qa_agent.core.config import settings


def _local_llm_config() -> dict:
    return {
        "api_url": settings.llm_api_url,
        "api_key": settings.llm_api_key,
        "model_name": settings.llm_model_name,
        "timeout": settings.llm_timeout,
    }


def _local_retrieval_config() -> dict:
    return {
        "knowledge_search_url": settings.knowledge_search_url,
        "top_k": settings.default_top_k,
        "similarity_threshold": settings.default_similarity_threshold,
        "rerank_threshold": settings.default_rerank_threshold,
    }


async def _fetch_json(path: str) -> dict | None:
    if not settings.java_config_base_url:
        return None

    url = f"{settings.java_config_base_url.rstrip('/')}/{path.lstrip('/')}"
    try:
        async with httpx.AsyncClient(timeout=settings.llm_timeout) as client:
            response = await client.get(url)
            response.raise_for_status()
            payload = response.json()
    except (httpx.HTTPError, ValueError):
        return None

    if isinstance(payload, dict) and isinstance(payload.get("data"), dict):
        return payload["data"]
    if isinstance(payload, dict):
        return payload
    return None


async def fetch_llm_config() -> dict:
    remote_config = await _fetch_json(settings.java_llm_config_path)
    return remote_config or _local_llm_config()


async def fetch_retrieval_config() -> dict:
    remote_config = await _fetch_json(settings.java_retrieval_config_path)
    return remote_config or _local_retrieval_config()


async def test_llm_connection() -> bool:
    config = await fetch_llm_config()
    api_key = config.get("api_key") or config.get("apiKey")
    api_url = (config.get("api_url") or config.get("apiUrl") or "").rstrip("/")
    model_name = config.get("model_name") or config.get("modelName")
    timeout = config.get("timeout") or settings.llm_timeout

    if not api_key or not api_url or not model_name:
        return False

    try:
        async with httpx.AsyncClient(timeout=timeout) as client:
            response = await client.post(
                f"{api_url}/chat/completions",
                headers={"Authorization": f"Bearer {api_key}"},
                json={
                    "model": model_name,
                    "messages": [{"role": "user", "content": "ping"}],
                    "max_tokens": 1,
                },
            )
            return response.status_code < 400
    except httpx.HTTPError:
        return False
