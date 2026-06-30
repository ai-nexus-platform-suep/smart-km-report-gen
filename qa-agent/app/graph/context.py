"""上下文管理：历史消息拼装 + Token 截断 (人员 C 独占)

原理：
  LLM 的上下文窗口有限（如 DeepSeek 默认 8K tokens）。
  多轮对话时，历史消息越来越多，必须截断才能不超限。

策略：
  1. 保留最新 N 轮对话（轮次优先）
  2. 每轮截断到 max_pairs * 2 条消息（一问一答 = 1 对）
  3. 超出 token 上限时，从最早的消息开始丢弃
  4. 始终保留 system prompt 和当前 user 问题
"""

import re
import math
from app.core.constants import (
    TOKENS_PER_CHINESE_CHAR,
    TOKENS_PER_ENGLISH_WORD,
    DEFAULT_MAX_CONTEXT_TOKENS,
)


def estimate_tokens(text: str) -> int:
    """粗略估算文本的 token 数。

    中文字符约 1.5 token/字，英文单词约 1 token/词，标点符号各算 1 token。
    这不是精确计数，但比单纯按字符数截断准确得多。
    """
    if not text:
        return 0

    chinese_chars = len(re.findall(r'[\u4e00-\u9fff\u3000-\u303f\uff00-\uffef]', text))
    english_words = len(re.findall(r'[a-zA-Z]+', text))
    other_chars = len(re.findall(r'[^\u4e00-\u9fff\u3000-\u303f\uff00-\uffefa-zA-Z\s]', text))

    return int(
        chinese_chars * TOKENS_PER_CHINESE_CHAR +
        english_words * TOKENS_PER_ENGLISH_WORD +
        other_chars
    )


def _format_message(msg: dict) -> str:
    """将单条消息格式化为 prompt 文本。"""
    role = msg.get("role", "user")
    content = msg.get("content", "")
    label = {"user": "用户", "assistant": "助手", "system": "系统"}.get(role, role)
    return f"{label}: {content}"


def build_context(
    messages: list,
    max_tokens: int = DEFAULT_MAX_CONTEXT_TOKENS,
    system_prompt: str = "",
    current_question: str = "",
) -> str:
    """将历史消息拼装为 LLM 的上下文文本。

    参数:
        messages:  历史消息列表 [{"role": "...", "content": "..."}, ...]
        max_tokens: 上下文最大 token 数
        system_prompt: system prompt（不计入截断）
        current_question: 当前用户问题（优先保留）

    返回:
        拼装后的上下文字符串，可直接放入 LLM prompt
    """
    if not messages:
        return ""

    # 第一步：分离 system 消息和对话消息
    dialog_msgs = [m for m in messages if m.get("role") != "system"]
    system_msgs = [m for m in messages if m.get("role") == "system"]

    # 计算固定开销（system + 当前问题）
    fixed_tokens = estimate_tokens(system_prompt) + estimate_tokens(current_question)
    for sm in system_msgs:
        fixed_tokens += estimate_tokens(sm.get("content", ""))

    available_tokens = max_tokens - fixed_tokens - 200  # 留 200 token 余量

    # 第二步：从最新到最旧排列，逐条加入直到 token 超限
    reversed_msgs = list(reversed(dialog_msgs))
    kept_lines: list[str] = []
    used_tokens = 0

    for msg in reversed_msgs:
        line = _format_message(msg)
        tokens = estimate_tokens(line)
        if used_tokens + tokens > available_tokens:
            break
        kept_lines.insert(0, line)  # 插入到开头（恢复正序）
        used_tokens += tokens

    # 如果被截断了，加一行提示
    if len(kept_lines) < len(dialog_msgs):
        kept_lines.insert(0, "[更早的对话内容已被截断]")

    return "\n".join(kept_lines)


def truncate_by_tokens(messages: list, max_tokens: int) -> list:
    """按 token 数量截断消息列表，返回截断后的子列表。

    从最新消息开始保留，丢弃最早的消息。

    参数:
        messages:   消息列表
        max_tokens: token 上限

    返回:
        截断后的消息子列表（保持原序）
    """
    if not messages or max_tokens <= 0:
        return []

    kept: list = []
    used = 0

    for msg in reversed(messages):
        content = msg.get("content", "") if isinstance(msg, dict) else str(msg)
        tokens = estimate_tokens(content)
        if used + tokens > max_tokens:
            break
        kept.insert(0, msg)
        used += tokens

    return kept


def count_context_tokens(messages: list) -> int:
    """统计消息列表的总 token 数（用于调试和监控）。"""
    total = 0
    for msg in messages:
        content = msg.get("content", "") if isinstance(msg, dict) else str(msg)
        total += estimate_tokens(content)
    return total
