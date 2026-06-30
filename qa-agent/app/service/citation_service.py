"""引用溯源：提取 + 标记插入 + 合并连续引用 (人员 C 独占)

原理：
  RAG 生成的回答引用了知识库中的文档片段。系统需要在回答中标明"这句话来自哪个文档"，
  并在前端提供悬浮预览和点击下载原文件的能力。

需求来源（需求说明书 3.4）：
  - 引用标注支持多个连续引用来自同一文档时的合并显示
  - 用户可查看引用对应的原文片段、来源文档名称、相关性分数
  - 引用弹出框支持悬浮预览和点击固定两种交互方式
  - 系统应支持通过引用的文档 ID 追溯并下载原文件

引用数据结构：
  {
    "index": 1,               # 引用编号（在回答中的标记位置）
    "doc_id": "doc_001",      # 文档 ID
    "doc_name": "技术监督管理办法.pdf",  # 文档名称
    "chapter_path": "第3章 > 第2节",    # 所在章节路径
    "snippet": "原文片段...",          # 引用原文（截取前 300 字）
    "score": 0.92,            # 相关性分数
    "chunk_type": "正文",     # 切片类型
  }
"""

import re
from typing import Optional


def build_citations(
    documents: list[dict],
    start_index: int = 1,
) -> list[dict]:
    """从检索到的文档列表构建引用数组。

    这是 generate_node 完成后调用的主入口。
    将 retrieved_docs 转换为标准的 citation 数组。

    参数:
        documents:   检索结果列表 [{"doc_id": ..., "doc_name": ..., "snippet": ..., "score": ...}, ...]
        start_index: 起始编号，默认从 1 开始

    返回:
        citation 数组
    """
    citations: list[dict] = []
    for i, doc in enumerate(documents, start=start_index):
        snippet = doc.get("snippet") or ""
        # 截取前 300 字符作为预览（需求：引用弹出框支持可滚动的长文本预览）
        preview = snippet[:300] + ("..." if len(snippet) > 300 else "")

        citations.append({
            "index": i,
            "doc_id": doc.get("doc_id"),
            "doc_name": doc.get("doc_name", "未知文档"),
            "chapter_path": doc.get("chapter_path", ""),
            "snippet": preview,
            "full_snippet": snippet,
            "score": doc.get("score"),
            "chunk_type": doc.get("chunk_type", "正文"),
        })
    return citations


def extract_citations(text: str) -> list[dict]:
    """从 LLM 生成的回答文本中提取引用标记。

    识别文本中的 [1]、[2,3]、[1-3] 等引用标记，返回标记位置列表。
    用于后处理：将 citation JSON 与文本中的标记位置关联。

    参数:
        text: LLM 生成的回答文本

    返回:
        引用标记列表 [{"index": 1, "start": 100, "end": 103}, ...]
    """
    pattern = r'\[(\d+(?:[-,]\d+)*)\]'
    results: list[dict] = []

    for match in re.finditer(pattern, text):
        raw = match.group(1)
        indices = _parse_indices(raw)
        if indices:
            results.append({
                "indices": indices,
                "start": match.start(),
                "end": match.end(),
                "raw": match.group(0),
            })

    return results


def insert_citation_marks(text: str, citations: list[dict]) -> str:
    """在回答文本中插入引用标记。

    参数:
        text:      LLM 生成的回答文本（可能不含标记）
        citations: build_citations() 的输出

    返回:
        带引用标记的文本（末尾追加 "[1] 文档名.pdf" 列表）
    """
    if not citations:
        return text

    # 在回答末尾追加引用列表（Markdown 格式，适合前端渲染）
    lines = [text.rstrip(), "", "---", "", "**引用来源：**"]
    for c in citations:
        idx = c.get("index", "?")
        name = c.get("doc_name", "未知文档")
        snippet = c.get("snippet", "")
        score = c.get("score")
        score_str = f" (相关性: {float(score):.2f})" if score is not None else ""
        lines.append(f"[{idx}] **{name}**{score_str}")
        if snippet:
            lines.append(f"    > {snippet}")

    return "\n".join(lines)


def merge_consecutive_citations(citations: list[dict]) -> list[dict]:
    """合并来自同一文档的连续引用（需求 3.4 明确要求）。

    当回答中连续多句都引用同一个文档时，前端展示应合并为一个引用标记，
    而不是每句单独标记。这减少视觉噪音。

    示例:
        输入: [{"doc_id": "d1", "index": 1}, {"doc_id": "d1", "index": 2}, {"doc_id": "d2", "index": 3}]
        输出: [{"doc_id": "d1", "indices": [1, 2]}, {"doc_id": "d2", "indices": [3]}]

    参数:
        citations: 原始引用数组（每个元素一个 index）

    返回:
        合并后的引用数组（同文档连续的合并为一条，indices 字段变为数组）
    """
    if not citations:
        return []

    merged: list[dict] = []
    for c in citations:
        doc_id = c.get("doc_id")
        # 检查是否与上一条同一文档
        if merged and merged[-1].get("doc_id") == doc_id:
            # 合并：追加 index
            prev_indices = merged[-1].get("indices", [merged[-1].get("index")])
            prev_indices.append(c.get("index"))
            merged[-1]["indices"] = prev_indices
            # 保留更高的 score
            prev_score = float(merged[-1].get("score") or 0)
            cur_score = float(c.get("score") or 0)
            if cur_score > prev_score:
                merged[-1]["score"] = cur_score
                merged[-1]["snippet"] = c.get("snippet", "")
        else:
            merged.append(dict(c))

    return merged


def citation_to_sse(citations: list[dict]) -> dict:
    """将合并后的引用数组转为 SSE citation 事件的 data 字段。

    前端收到的 SSE citation 事件:
        {"type": "citation", "citations": [...], "merged": true}
    """
    return {
        "type": "citation",
        "citations": citations,
        "merged": True,
    }


def _parse_indices(raw: str) -> list[int]:
    """解析引用标记原始文本为索引列表。

    支持格式:
        "1"       → [1]
        "2,3,5"   → [2, 3, 5]
        "1-3"     → [1, 2, 3]
    """
    result: list[int] = []
    parts = raw.split(",")
    for part in parts:
        part = part.strip()
        if "-" in part:
            try:
                start, end = part.split("-", 1)
                result.extend(range(int(start), int(end) + 1))
            except ValueError:
                continue
        else:
            try:
                result.append(int(part))
            except ValueError:
                continue
    return sorted(set(result))
