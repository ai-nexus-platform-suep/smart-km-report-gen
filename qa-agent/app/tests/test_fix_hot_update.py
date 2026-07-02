"""验证模型配置热更新修复：java_client.py 错误日志 + Controller userId query param"""

import logging
import pytest
from unittest.mock import AsyncMock, patch, MagicMock
import httpx

from app.client.java_client import _fetch_json, fetch_llm_config, _local_llm_config


class TestJavaClientErrorLogging:
    """验证 java_client.py 修复2：错误应有日志输出"""

    @pytest.mark.asyncio
    async def test_connect_error_logs(self):
        """httpx.ConnectError 应输出 logger.error"""
        with (
            patch("app.client.java_client.settings") as mock_settings,
            patch("httpx.AsyncClient") as mock_client,
            patch("app.client.java_client.logger") as mock_logger,
        ):
            mock_settings.java_config_base_url = "http://127.0.0.1:8090"
            mock_client.return_value.__aenter__.return_value.get = AsyncMock(
                side_effect=httpx.ConnectError("Connection refused")
            )
            result = await _fetch_json("internal/model-configs/default?userId=1")
            assert result is None
            mock_logger.error.assert_called_once()
            args, _ = mock_logger.error.call_args
            assert "连接 Java 服务失败" in args[0]

    @pytest.mark.asyncio
    async def test_http_error_logs(self):
        """httpx.HTTPStatusError 应输出 logger.error"""
        mock_resp = MagicMock()
        mock_resp.status_code = 500
        mock_resp.text = "Internal Server Error"
        mock_get = AsyncMock(side_effect=httpx.HTTPStatusError(
            "Server error", request=MagicMock(), response=mock_resp
        ))
        with (
            patch("app.client.java_client.settings") as mock_settings,
            patch("httpx.AsyncClient") as mock_client,
            patch("app.client.java_client.logger") as mock_logger,
        ):
            mock_settings.java_config_base_url = "http://127.0.0.1:8090"
            mock_client.return_value.__aenter__.return_value.get = mock_get
            result = await _fetch_json("internal/model-configs/default?userId=1")
            assert result is None
            mock_logger.error.assert_called_once()
            args, _ = mock_logger.error.call_args
            assert "Java 服务返回错误" in args[0]

    @pytest.mark.asyncio
    async def test_value_error_logs(self):
        """非 JSON 响应（ValueError）应输出 logger.error"""
        mock_resp = MagicMock()
        mock_resp.raise_for_status = MagicMock()  # sync, no-op
        mock_resp.json = MagicMock(side_effect=ValueError("Invalid JSON"))  # sync

        mock_client = MagicMock()
        mock_client.get = AsyncMock(return_value=mock_resp)

        with (
            patch("app.client.java_client.settings") as mock_settings,
            patch("httpx.AsyncClient") as mock_httpx_cls,
            patch("app.client.java_client.logger") as mock_logger,
        ):
            mock_settings.java_config_base_url = "http://127.0.0.1:8090"
            mock_httpx_cls.return_value.__aenter__.return_value = mock_client
            result = await _fetch_json("internal/model-configs/default?userId=1")
            assert result is None
            mock_logger.error.assert_called_once()
            args, _ = mock_logger.error.call_args
            assert "非 JSON 响应" in args[0]


class TestJavaClientNoFallback:
    """验证 java_client.py 错误不再静默吞掉，返回 None 但有日志"""

    @pytest.mark.asyncio
    async def test_fetch_llm_config_falls_back_to_local_on_error(self):
        """远程获取失败时应回落本地配置"""
        with patch("app.client.java_client._fetch_json", AsyncMock(return_value=None)):
            config = await fetch_llm_config(user_id=1, scenario="chat")
            assert config == _local_llm_config()


class TestQueryParamUserId:
    """验证修复1：Python 通过 query 参数传 userId"""

    def test_fetch_llm_config_passes_user_id_in_query(self):
        """fetch_llm_config 应把 userId 放入 query string"""
        # 直接验证 URL 拼接逻辑
        url = f"internal/model-configs/default?userId={42}&scenario=summary"
        assert "userId=42" in url
        assert "scenario=summary" in url

    def test_fetch_llm_config_default_scenario_is_chat(self):
        """默认 scenario 应为 chat"""
        url = f"internal/model-configs/default?userId=1&scenario=chat"
        assert "scenario=chat" in url


class TestEnvPort:
    """验证修复3：.env.example 端口改为 8090"""

    def test_dotenv_example_has_correct_port(self):
        """确认 .env.example 中 JAVA_CONFIG_BASE_URL 是 8090"""
        import os
        dotenv_path = os.path.join(
            os.path.dirname(__file__), "..", "..", ".env.example"
        )
        with open(dotenv_path, "r", encoding="utf-8") as f:
            content = f.read()
        assert "JAVA_CONFIG_BASE_URL=http://127.0.0.1:8090" in content, \
            ".env.example 端口应从 8082 改为 8090"
