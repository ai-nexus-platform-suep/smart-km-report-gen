"""思考过程生成逻辑 (人员 C 独占)

原理：
  Agent 工作流执行时，每个节点（意图识别 → 检索 → 重排序 → 生成）都会产出一个
  思考步骤。前端通过 SSE 收到这些步骤，实时展示"AI 正在做什么"，减少等待焦虑。

数据流：
  intent_node → add_thinking_step("intent", "识别意图: KNOWLEDGE_QA")
  retrieve_node → add_thinking_step("retrieve", "检索到 5 条片段", elapsed_ms=320)
  rerank_node → add_thinking_step("rerank", "重排序后保留 3 条片段", elapsed_ms=15)
  generate_node → add_thinking_step("generate", "回答生成完成", elapsed_ms=2100)

前端展示（需求说明书 3.5）：
  - 思考步骤支持折叠和展开，默认在流式完成后自动折叠
  - 思考步骤支持代码内容与文本内容的区分展示
"""

import time
from typing import Optional
from qa_agent.core.constants import ThinkingStepType


def add_thinking_step(
    steps: list[dict],
    event_type: str,
    message: str,
    elapsed_ms: Optional[float] = None,
) -> list[dict]:
    """向思考步骤列表新增一步。

    参数:
        steps:      当前步骤列表
        event_type: 步骤类型 (intent/retrieve/rerank/generate/citation)
        message:    展示给用户的文本
        elapsed_ms: 本步骤耗时（毫秒），前端可据此判断性能

    返回:
        更新后的步骤列表（新建列表，不修改原列表）

    示例:
        >>> steps = []
        >>> steps = add_thinking_step(steps, "intent", "识别意图: KNOWLEDGE_QA")
        >>> steps
        [{"type": "intent", "message": "识别意图: KNOWLEDGE_QA", "timestamp": 1.23, "elapsed_ms": None}]
    """
    step = {
        "type": event_type,
        "message": message,
        "timestamp": time.time(),
        "elapsed_ms": elapsed_ms,
    }
    # 新建列表，避免副作用
    return steps + [step]


def build_thinking_summary(steps: list[dict]) -> str:
    """将思考步骤列表转为可读的摘要文本。

    用于非流式场景：ai.invoke() 后返回一段总结而非 SSE 事件流。

    示例:
        >>> steps = [
        ...     {"type": "intent", "message": "识别意图: KNOWLEDGE_QA"},
        ...     {"type": "retrieve", "message": "检索到 5 条片段"},
        ...     {"type": "generate", "message": "回答生成完成", "elapsed_ms": 1200},
        ... ]
        >>> print(build_thinking_summary(steps))
        1. [意图识别] 识别意图: KNOWLEDGE_QA
        2. [检索] 检索到 5 条片段
        3. [生成] 回答生成完成 (1200ms)
    """
    if not steps:
        return ""

    lines: list[str] = []
    for i, step in enumerate(steps, start=1):
        type_label = _type_label(step.get("type", ""))
        msg = step.get("message", "")
        elapsed = step.get("elapsed_ms")
        if elapsed is not None:
            lines.append(f"{i}. [{type_label}] {msg} ({elapsed:.0f}ms)")
        else:
            lines.append(f"{i}. [{type_label}] {msg}")
    return "\n".join(lines)


def to_sse_event(step: dict) -> dict:
    """将思考步骤转换为 SSE 事件的 data 字段。

    前端收到的 SSE thinking 事件格式:
        {"type": "thinking", "step_type": "retrieve", "message": "检索到 5 条片段", "elapsed_ms": 320}
    """
    return {
        "type": "thinking",
        "step_type": step.get("type", ""),
        "message": step.get("message", ""),
        "elapsed_ms": step.get("elapsed_ms"),
    }


def steps_total_time(steps: list[dict]) -> float:
    """计算所有步骤的总耗时（毫秒），有 elapsed_ms 的累加，没有的跳过。"""
    return sum(s.get("elapsed_ms") or 0 for s in steps)


def _type_label(event_type: str) -> str:
    return {
        ThinkingStepType.INTENT: "意图识别",
        ThinkingStepType.RETRIEVE: "检索",
        ThinkingStepType.RERANK: "重排序",
        ThinkingStepType.GENERATE: "生成",
        ThinkingStepType.CITATION: "引用",
    }.get(event_type, event_type)
