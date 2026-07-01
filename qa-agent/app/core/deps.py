"""FastAPI 依赖：从 UserContext 获取当前用户。"""

from fastapi import HTTPException

from app.core.user_context import get_user_id


def require_user_id() -> int:
    try:
        return get_user_id()
    except RuntimeError as exc:
        raise HTTPException(status_code=401, detail=str(exc)) from exc
