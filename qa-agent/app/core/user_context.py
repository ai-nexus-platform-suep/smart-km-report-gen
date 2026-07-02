"""请求级用户上下文，对标 Java UserContextHolder。"""

from contextvars import ContextVar
from dataclasses import dataclass, field

HEADER_USER_ID = "user-id"
HEADER_USERNAME = "username"
HEADER_ROLES = "roles"
HEADER_PERMISSIONS = "permissions"


@dataclass
class UserContext:
    user_id: int | None = None
    username: str | None = None
    roles: list[str] = field(default_factory=list)
    permissions: list[str] = field(default_factory=list)


_ctx: ContextVar[UserContext | None] = ContextVar("user_context", default=None)


def set_context(ctx: UserContext) -> None:
    _ctx.set(ctx)


def get_context() -> UserContext | None:
    return _ctx.get()


def get_user_id() -> int:
    ctx = _ctx.get()
    if ctx is None or ctx.user_id is None:
        raise RuntimeError("用户上下文未初始化，请确认请求经 Gateway 转发并携带有效 Token")
    return ctx.user_id


def get_username() -> str | None:
    ctx = _ctx.get()
    return ctx.username if ctx else None


def get_roles() -> list[str]:
    ctx = _ctx.get()
    return list(ctx.roles) if ctx else []


def get_permissions() -> list[str]:
    ctx = _ctx.get()
    return list(ctx.permissions) if ctx else []


def has_role(role_code: str) -> bool:
    return role_code in get_roles()


def has_permission(perm_code: str) -> bool:
    return perm_code in get_permissions()


def clear() -> None:
    _ctx.set(None)


def parse_csv_header(value: str | None) -> list[str]:
    if not value:
        return []
    return [part.strip() for part in value.split(",") if part.strip()]
