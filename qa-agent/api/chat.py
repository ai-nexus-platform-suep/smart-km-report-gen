"""POST /chat → SSE 流式 (人员 B 独占)"""

from fastapi import APIRouter
from sse_starlette.sse import EventSourceResponse

router = APIRouter(tags=["chat"])


@router.post("/chat")
async def chat():
    pass
