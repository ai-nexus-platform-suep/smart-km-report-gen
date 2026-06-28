"""SSE 事件类型枚举 (人员 C 独占)"""

from enum import StrEnum


class SSEEventType(StrEnum):
    THINKING = "thinking"
    MESSAGE = "message"
    CITATION = "citation"
    ERROR = "error"
    DONE = "done"
    STOP = "stop"
