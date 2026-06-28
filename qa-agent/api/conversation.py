"""GET/POST/DELETE /conversations (人员 B 独占)"""

from fastapi import APIRouter

router = APIRouter(tags=["conversations"])


@router.get("/conversations")
async def list_conversations():
    pass


@router.post("/conversations")
async def create_conversation():
    pass


@router.delete("/conversations/{conversation_id}")
async def delete_conversation(conversation_id: int):
    pass


@router.get("/conversations/{conversation_id}/messages")
async def get_messages(conversation_id: int):
    pass


@router.patch("/conversations/{conversation_id}")
async def update_conversation(conversation_id: int):
    pass
