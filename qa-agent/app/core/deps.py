"""FastAPI 依赖：从 UserContext 获取当前用户。"""

from fastapi import HTTPException

from app.core.user_context import (
    get_permissions,
    get_user_id,
    has_role,
    has_permission,
)


def require_user_id() -> int:
    try:
        return get_user_id()
    except RuntimeError as exc:
        raise HTTPException(status_code=401, detail=str(exc)) from exc


def require_permission(perm_code: str) -> None:
    """
    校验当前用户是否拥有指定权限码。

    - ROLE_SUPER_ADMIN 自动放行
    - 否则检查权限列表中是否包含 perm_code
    """
    try:
        get_user_id()  # 确保已登录
    except RuntimeError as exc:
        raise HTTPException(status_code=401, detail=str(exc)) from exc

    if has_role("ROLE_SUPER_ADMIN"):
        return

    if not has_permission(perm_code):
        raise HTTPException(status_code=403, detail=f"没有操作权限，所需权限码: {perm_code}")


def require_any_permission(*perm_codes: str) -> None:
    """
    校验当前用户是否拥有至少一个指定权限码。

    - ROLE_SUPER_ADMIN 自动放行
    """
    try:
        get_user_id()
    except RuntimeError as exc:
        raise HTTPException(status_code=401, detail=str(exc)) from exc

    if has_role("ROLE_SUPER_ADMIN"):
        return

    owned = set(get_permissions())
    if not any(code in owned for code in perm_codes):
        raise HTTPException(
            status_code=403,
            detail=f"没有操作权限，所需权限码之一: {', '.join(perm_codes)}",
        )
