"""UserContext 与 Gateway 请求头中间件单元测试。

运行:
  python -m pytest qa-agent/app/tests/test_user_context.py -v
"""

import pytest
from fastapi import FastAPI
from fastapi.testclient import TestClient

from app.core.deps import require_user_id
from app.core.user_context import (
    HEADER_PERMISSIONS,
    HEADER_ROLES,
    HEADER_USER_ID,
    HEADER_USERNAME,
    get_permissions,
    get_roles,
    get_user_id,
    get_username,
    parse_csv_header,
)
from app.core.user_context_middleware import UserContextMiddleware


def test_parse_csv_header():
    assert parse_csv_header("a,b, c") == ["a", "b", "c"]
    assert parse_csv_header("") == []
    assert parse_csv_header(None) == []


@pytest.fixture
def client():
    app = FastAPI()
    app.add_middleware(UserContextMiddleware)

    @app.get("/whoami")
    def whoami():
        return {
            "user_id": get_user_id(),
            "username": get_username(),
            "roles": get_roles(),
            "permissions": get_permissions(),
        }

    @app.get("/require")
    def require():
        return {"user_id": require_user_id()}

    return TestClient(app)


def test_middleware_populates_context(client):
    response = client.get(
        "/whoami",
        headers={
            HEADER_USER_ID: "42",
            HEADER_USERNAME: "alice",
            HEADER_ROLES: "ROLE_USER,ROLE_ADMIN",
            HEADER_PERMISSIONS: "chat:conversation:use,chat:model:view",
        },
    )
    assert response.status_code == 200
    body = response.json()
    assert body["user_id"] == 42
    assert body["username"] == "alice"
    assert "ROLE_USER" in body["roles"]
    assert "chat:model:view" in body["permissions"]


def test_require_user_id_missing_returns_401(client):
    response = client.get("/require")
    assert response.status_code == 401


def test_context_cleared_between_requests(client):
    ok = client.get("/whoami", headers={HEADER_USER_ID: "1"})
    assert ok.status_code == 200

    missing = client.get("/require")
    assert missing.status_code == 401
