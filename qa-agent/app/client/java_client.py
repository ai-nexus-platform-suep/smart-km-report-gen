"""调用 Java 服务拉取模型配置 (人员 A 独占)"""

import httpx

from app.core.config import settings


def _local_llm_config() -> dict:
    return {
        "provider": "deepseek",
        "base_url": settings.llm_api_url,
        "model_name": settings.llm_model_name,
        "api_key": settings.llm_api_key,
        "timeout_seconds": settings.llm_timeout,
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


async def fetch_llm_config(user_id: int, scenario: str = "chat") -> dict:
    """从 Java 获取指定用户在某场景下的默认模型配置（apiKey 已解密）"""
    remote_config = await _fetch_json(
        f"internal/model-configs/default?userId={user_id}&scenario={scenario}"
    )
    if remote_config:
        # Java 返回 camelCase，转为 snake_case 内部使用
        return {
            "provider": remote_config.get("provider", "deepseek"),
            "base_url": remote_config.get("baseUrl", ""),
            "model_name": remote_config.get("modelName", ""),
            "api_key": remote_config.get("apiKey", ""),
            "timeout_seconds": remote_config.get("timeoutSeconds", settings.llm_timeout),
        }
    return _local_llm_config()


async def fetch_retrieval_config() -> dict:
    return {
        "knowledge_search_url": settings.knowledge_search_url,
        "top_k": settings.default_top_k,
        "similarity_threshold": settings.default_similarity_threshold,
        "rerank_threshold": settings.default_rerank_threshold,
    }


async def test_llm_connection() -> bool:
    config = await fetch_llm_config()
    api_key = config.get("api_key", "")
    base_url = (config.get("base_url") or "").rstrip("/")
    model_name = config.get("model_name", "")
    timeout = config.get("timeout_seconds", settings.llm_timeout)

    if not api_key or not base_url or not model_name:
        return False

    try:
        async with httpx.AsyncClient(timeout=timeout) as client:
            response = await client.post(
                f"{base_url}/chat/completions",
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
