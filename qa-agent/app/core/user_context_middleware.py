"""从 Gateway 注入的请求头恢复用户上下文。"""

from starlette.middleware.base import BaseHTTPMiddleware
from starlette.requests import Request

from app.core.user_context import (
    HEADER_PERMISSIONS,
    HEADER_ROLES,
    HEADER_USER_ID,
    HEADER_USERNAME,
    UserContext,
    clear,
    parse_csv_header,
    set_context,
)


class UserContextMiddleware(BaseHTTPMiddleware):
    async def dispatch(self, request: Request, call_next):
        user_id_raw = request.headers.get(HEADER_USER_ID)
        user_id = int(user_id_raw) if user_id_raw and user_id_raw.isdigit() else None

        set_context(
            UserContext(
                user_id=user_id,
                username=request.headers.get(HEADER_USERNAME),
                roles=parse_csv_header(request.headers.get(HEADER_ROLES)),
                permissions=parse_csv_header(request.headers.get(HEADER_PERMISSIONS)),
            )
        )
        try:
            return await call_next(request)
        finally:
            clear()
