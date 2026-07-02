"""SSE 事件类型 + 思考步骤类型枚举 (人员 C 独占)"""

from enum import Enum


class SSEEventType(str, Enum):
    """SSE 推送给前端的事件类型"""
    THINKING = "thinking"
    MESSAGE = "message"
    CITATION = "citation"
    ERROR = "error"
    DONE = "done"
    STOP = "stop"


class ThinkingStepType(str, Enum):
    """思考步骤内部类型"""
    INTENT = "intent"
    RETRIEVE = "retrieve"
    RERANK = "rerank"
    GENERATE = "generate"
    CITATION = "citation"


# 意图枚举值（与 nodes.py 保持同步）
KNOWLEDGE_QA = "KNOWLEDGE_QA"
CHAT = "CHAT"
DOCUMENT_SEARCH = "DOCUMENT_SEARCH"

# Token 估算常量
TOKENS_PER_CHINESE_CHAR = 1.5    # 中文字符约 1.5 token
TOKENS_PER_ENGLISH_WORD = 1.0    # 英文单词约 1 token
DEFAULT_MAX_CONTEXT_TOKENS = 8192
