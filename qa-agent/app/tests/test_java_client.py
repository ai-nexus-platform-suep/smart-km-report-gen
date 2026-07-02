"""java_client.py 单元测试：验证 user-id Header 传递与错误日志。

运行:
  python -m pytest qa-agent/app/tests/test_java_client.py -v

注意：本测试使用 monkeypatch + unittest.mock 模拟 httpx 请求，
      不依赖 Java 服务实际运行。
"""

from unittest.mock import ANY, AsyncMock, MagicMock, patch

import httpx
import pytest

from app.client.java_client import (
    _fetch_json,
    _local_llm_config,
    fetch_llm_config,
)


# ============================================================================
# _local_llm_config 回退逻辑
# ============================================================================

class TestLocalFallback:
    """本地 .env 配置回退"""

    def test_returns_env_settings(self, monkeypatch):
        from app.core.config import settings
        monkeypatch.setattr(settings, "llm_api_url", "https://custom.deepseek.com")
        monkeypatch.setattr(settings, "llm_model_name", "deepseek-v3")
        monkeypatch.setattr(settings, "llm_api_key", "sk-local")
        monkeypatch.setattr(settings, "llm_timeout", 120)

        cfg = _local_llm_config()
        assert cfg["provider"] == "deepseek"
        assert cfg["base_url"] == "https://custom.deepseek.com"
        assert cfg["model_name"] == "deepseek-v3"
        assert cfg["api_key"] == "sk-local"
        assert cfg["timeout_seconds"] == 120


# ============================================================================
# _fetch_json - Header 传递
# ============================================================================

class TestFetchJson:
    """_fetch_json 请求构造"""

    @pytest.fixture(autouse=True)
    def setup_settings(self, monkeypatch):
        from app.core.config import settings
        monkeypatch.setattr(settings, "java_config_base_url", "http://127.0.0.1:8090")
        monkeypatch.setattr(settings, "llm_timeout", 10)
        return settings

    @pytest.mark.asyncio
    async def test_passes_headers_to_request(self):
        """验证 _fetch_json 将 headers 参数正确传递给 httpx.get"""
        mock_response = MagicMock(spec=httpx.Response)
        mock_response.status_code = 200
        mock_response.json.return_value = {"data": {"provider": "deepseek"}}
        mock_response.raise_for_status = MagicMock()

        mock_client = AsyncMock()
        mock_client.get.return_value = mock_response
        mock_client.__aenter__.return_value = mock_client
        mock_client.__aexit__.return_value = None

        with patch("httpx.AsyncClient", return_value=mock_client):
            result = await _fetch_json(
                "internal/model-configs/default?scenario=chat",
                headers={"user-id": "42"},
            )

        # 验证 headers 被传递
        call_kwargs = mock_client.get.call_args
        assert call_kwargs is not None
        # httpx.get(url, headers=...)  → call_args.kwargs["headers"]
        passed_headers = call_kwargs.kwargs.get("headers", {})
        assert passed_headers.get("user-id") == "42"

    @pytest.mark.asyncio
    async def test_no_headers_does_not_break(self):
        """_fetch_json 不传 headers 时仍正常工作"""
        mock_response = MagicMock(spec=httpx.Response)
        mock_response.status_code = 200
        mock_response.json.return_value = {"data": {"provider": "deepseek"}}
        mock_response.raise_for_status = MagicMock()

        mock_client = AsyncMock()
        mock_client.get.return_value = mock_response
        mock_client.__aenter__.return_value = mock_client
        mock_client.__aexit__.return_value = None

        with patch("httpx.AsyncClient", return_value=mock_client):
            result = await _fetch_json("internal/model-configs/default?scenario=chat")

        call_kwargs = mock_client.get.call_args
        assert call_kwargs.kwargs.get("headers") is None
        assert result == {"provider": "deepseek"}


# ============================================================================
# _fetch_json - 错误处理 & 日志
# ============================================================================

class TestFetchJsonErrors:
    """_fetch_json 异常处理"""

    @pytest.fixture(autouse=True)
    def setup_settings(self, monkeypatch):
        from app.core.config import settings
        monkeypatch.setattr(settings, "java_config_base_url", "http://127.0.0.1:8090")
        monkeypatch.setattr(settings, "llm_timeout", 10)
        return settings

    @pytest.mark.asyncio
    async def test_connect_error_logs_and_returns_none(self, caplog):
        """连接失败时应记录 ERROR 日志并返回 None"""
        mock_client = AsyncMock()
        mock_client.get.side_effect = httpx.ConnectError("Connection refused")
        mock_client.__aenter__.return_value = mock_client
        mock_client.__aexit__.return_value = None

        with patch("httpx.AsyncClient", return_value=mock_client):
            with caplog.at_level("ERROR"):
                result = await _fetch_json("internal/model-configs/default?scenario=chat")

        assert result is None
        assert "连接 Java 配置服务失败" in caplog.text

    @pytest.mark.asyncio
    async def test_http_status_error_logs_and_returns_none(self, caplog):
        """HTTP 状态错误（4xx/5xx）时应记录 ERROR 日志并返回 None"""
        mock_response = MagicMock(spec=httpx.Response)
        mock_response.status_code = 500
        mock_response.text = "Internal Server Error"
        mock_response.raise_for_status.side_effect = httpx.HTTPStatusError(
            "Server error", request=MagicMock(), response=mock_response
        )

        mock_client = AsyncMock()
        mock_client.get.return_value = mock_response
        mock_client.__aenter__.return_value = mock_client
        mock_client.__aexit__.return_value = None

        with patch("httpx.AsyncClient", return_value=mock_client):
            with caplog.at_level("ERROR"):
                result = await _fetch_json("internal/model-configs/default?scenario=chat")

        assert result is None
        assert "Java 配置服务返回错误" in caplog.text
        assert "HTTP 500" in caplog.text

    @pytest.mark.asyncio
    async def test_other_http_error_logs_and_returns_none(self, caplog):
        """其他 HTTP 错误（超时等）时应记录 WARNING 并返回 None"""
        mock_client = AsyncMock()
        mock_client.get.side_effect = httpx.ReadTimeout("timed out")
        mock_client.__aenter__.return_value = mock_client
        mock_client.__aexit__.return_value = None

        with patch("httpx.AsyncClient", return_value=mock_client):
            with caplog.at_level("WARNING"):
                result = await _fetch_json("internal/model-configs/default?scenario=chat")

        assert result is None
        assert "HTTP 请求异常" in caplog.text

    @pytest.mark.asyncio
    async def test_json_parse_error_logs_and_returns_none(self, caplog):
        """JSON 解析失败时应记录 WARNING 并返回 None"""
        mock_response = MagicMock(spec=httpx.Response)
        mock_response.status_code = 200
        mock_response.json.side_effect = ValueError("Invalid JSON")
        mock_response.raise_for_status = MagicMock()

        mock_client = AsyncMock()
        mock_client.get.return_value = mock_response
        mock_client.__aenter__.return_value = mock_client
        mock_client.__aexit__.return_value = None

        with patch("httpx.AsyncClient", return_value=mock_client):
            with caplog.at_level("WARNING"):
                result = await _fetch_json("internal/model-configs/default?scenario=chat")

        assert result is None
        assert "JSON 解析失败" in caplog.text

    @pytest.mark.asyncio
    async def test_no_base_url_configured(self, caplog):
        """JAVA_CONFIG_BASE_URL 未配置时记录 WARNING 并返回 None"""
        from app.core.config import settings

        def _reset_and_call(create_settings):
            return create_settings

        with patch.object(settings, "java_config_base_url", ""):
            result = await _fetch_json("internal/model-configs/default?scenario=chat")

        assert result is None


# ============================================================================
# fetch_llm_config - 集成流程
# ============================================================================

class TestFetchLlmConfig:
    """fetch_llm_config 完整流程"""

    @pytest.fixture(autouse=True)
    def setup_settings(self, monkeypatch):
        from app.core.config import settings
        monkeypatch.setattr(settings, "java_config_base_url", "http://127.0.0.1:8090")
        monkeypatch.setattr(settings, "llm_timeout", 60)
        monkeypatch.setattr(settings, "llm_api_url", "https://api.deepseek.com")
        monkeypatch.setattr(settings, "llm_model_name", "deepseek-chat")
        monkeypatch.setattr(settings, "llm_api_key", "sk-local-fallback")
        return settings

    @pytest.mark.asyncio
    async def test_sends_user_id_header(self):
        """验证 fetch_llm_config 通过 user-id Header 传递 userId"""
        mock_response = MagicMock(spec=httpx.Response)
        mock_response.status_code = 200
        mock_response.json.return_value = {
            "data": {
                "provider": "openai",
                "baseUrl": "https://api.openai.com",
                "modelName": "gpt-4",
                "apiKey": "sk-remote-key",
                "timeoutSeconds": 30,
            }
        }
        mock_response.raise_for_status = MagicMock()

        mock_client = AsyncMock()
        mock_client.get.return_value = mock_response
        mock_client.__aenter__.return_value = mock_client
        mock_client.__aexit__.return_value = None

        with patch("httpx.AsyncClient", return_value=mock_client):
            result = await fetch_llm_config(user_id=42, scenario="chat")

        # 验证请求头
        call_kwargs = mock_client.get.call_args
        headers = call_kwargs.kwargs.get("headers", {})
        assert headers.get("user-id") == "42"

        # 验证 URL 不含 userId query param
        url = call_kwargs.args[0]
        assert "userId=" not in url
        assert "scenario=chat" in url

        # 验证返回结果
        assert result["provider"] == "openai"
        assert result["base_url"] == "https://api.openai.com"
        assert result["model_name"] == "gpt-4"
        assert result["api_key"] == "sk-remote-key"
        assert result["timeout_seconds"] == 30

    @pytest.mark.asyncio
    async def test_falls_back_to_local_when_remote_fails(self):
        """远程获取失败时退回本地 .env 配置"""
        mock_client = AsyncMock()
        mock_client.get.side_effect = httpx.ConnectError("refused")
        mock_client.__aenter__.return_value = mock_client
        mock_client.__aexit__.return_value = None

        with patch("httpx.AsyncClient", return_value=mock_client):
            result = await fetch_llm_config(user_id=1, scenario="chat")

        assert result["provider"] == "deepseek"
        assert result["base_url"] == "https://api.deepseek.com"
        assert result["api_key"] == "sk-local-fallback"

    @pytest.mark.asyncio
    async def test_camel_to_snake_field_mapping(self):
        """验证 Java 返回的 camelCase 字段正确映射为 snake_case"""
        mock_response = MagicMock(spec=httpx.Response)
        mock_response.status_code = 200
        mock_response.json.return_value = {
            "data": {
                "provider": "anthropic",
                "baseUrl": "https://api.anthropic.com",
                "modelName": "claude-3",
                "apiKey": "sk-anthropic",
                "timeoutSeconds": 90,
            }
        }
        mock_response.raise_for_status = MagicMock()

        mock_client = AsyncMock()
        mock_client.get.return_value = mock_response
        mock_client.__aenter__.return_value = mock_client
        mock_client.__aexit__.return_value = None

        with patch("httpx.AsyncClient", return_value=mock_client):
            result = await fetch_llm_config(user_id=1, scenario="summary")

        assert result["provider"] == "anthropic"
        assert result["base_url"] == "https://api.anthropic.com"
        assert result["model_name"] == "claude-3"
        assert result["api_key"] == "sk-anthropic"
        assert result["timeout_seconds"] == 90

    @pytest.mark.asyncio
    async def test_default_scenario_is_chat(self):
        """scenario 默认值为 chat"""
        mock_response = MagicMock(spec=httpx.Response)
        mock_response.status_code = 200
        mock_response.json.return_value = {
            "data": {"provider": "deepseek", "baseUrl": "", "modelName": "", "apiKey": "", "timeoutSeconds": 60}
        }
        mock_response.raise_for_status = MagicMock()

        mock_client = AsyncMock()
        mock_client.get.return_value = mock_response
        mock_client.__aenter__.return_value = mock_client
        mock_client.__aexit__.return_value = None

        with patch("httpx.AsyncClient", return_value=mock_client):
            await fetch_llm_config(user_id=5)  # 不传 scenario

        url = mock_client.get.call_args.args[0]
        assert "scenario=chat" in url

    @pytest.mark.asyncio
    async def test_missing_fields_use_defaults(self):
        """Java 返回缺少字段时使用默认值"""
        mock_response = MagicMock(spec=httpx.Response)
        mock_response.status_code = 200
        mock_response.json.return_value = {
            "data": {
                "provider": "deepseek",
                "apiKey": "sk-key-only",
            }
        }
        mock_response.raise_for_status = MagicMock()

        mock_client = AsyncMock()
        mock_client.get.return_value = mock_response
        mock_client.__aenter__.return_value = mock_client
        mock_client.__aexit__.return_value = None

        with patch("httpx.AsyncClient", return_value=mock_client):
            result = await fetch_llm_config(user_id=1)

        assert result["provider"] == "deepseek"
        assert result["base_url"] == ""          # 默认空
        assert result["model_name"] == ""         # 默认空
        assert result["api_key"] == "sk-key-only"
        assert result["timeout_seconds"] == 60    # 从 settings.llm_timeout 取值

    @pytest.mark.asyncio
    async def test_payload_without_data_wrapper(self):
        """Java 返回体无 data 包裹时直接使用 payload"""
        mock_response = MagicMock(spec=httpx.Response)
        mock_response.status_code = 200
        mock_response.json.return_value = {
            "provider": "deepseek",
            "baseUrl": "https://api.deepseek.com",
            "modelName": "deepseek-v3",
            "apiKey": "sk-direct",
            "timeoutSeconds": 45,
        }
        mock_response.raise_for_status = MagicMock()

        mock_client = AsyncMock()
        mock_client.get.return_value = mock_response
        mock_client.__aenter__.return_value = mock_client
        mock_client.__aexit__.return_value = None

        with patch("httpx.AsyncClient", return_value=mock_client):
            result = await fetch_llm_config(user_id=1)

        assert result["api_key"] == "sk-direct"


# ============================================================================
# 不同 userId 场景
# ============================================================================

class TestMultiUser:
    """多用户场景：不同 userId 获取不同配置"""

    @pytest.fixture(autouse=True)
    def setup_settings(self, monkeypatch):
        from app.core.config import settings
        monkeypatch.setattr(settings, "java_config_base_url", "http://127.0.0.1:8090")
        monkeypatch.setattr(settings, "llm_timeout", 60)
        monkeypatch.setattr(settings, "llm_api_url", "https://api.deepseek.com")
        monkeypatch.setattr(settings, "llm_model_name", "deepseek-chat")
        monkeypatch.setattr(settings, "llm_api_key", "sk-local")
        return settings

    @pytest.mark.asyncio
    async def test_different_users_get_different_headers(self):
        """不同 userId 的请求应携带不同的 user-id Header"""
        captured_headers = []

        def _mock_get(url, headers=None, **kwargs):
            captured_headers.append(headers or {})
            mock_resp = MagicMock(spec=httpx.Response)
            mock_resp.status_code = 200
            mock_resp.json.return_value = {
                "data": {"provider": "p", "baseUrl": "u", "modelName": "m", "apiKey": "k", "timeoutSeconds": 1}
            }
            mock_resp.raise_for_status = MagicMock()
            return mock_resp

        mock_client = AsyncMock()
        mock_client.get.side_effect = _mock_get
        mock_client.__aenter__.return_value = mock_client
        mock_client.__aexit__.return_value = None

        with patch("httpx.AsyncClient", return_value=mock_client):
            await fetch_llm_config(user_id=1)
            await fetch_llm_config(user_id=99)
            await fetch_llm_config(user_id=1)

        assert len(captured_headers) == 3
        assert captured_headers[0]["user-id"] == "1"
        assert captured_headers[1]["user-id"] == "99"
        assert captured_headers[2]["user-id"] == "1"
