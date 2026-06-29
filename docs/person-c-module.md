# 人员 C 模块 — 架构与实现文档

> 分支: feat/b-context | 人员: C | 2026-06-29

---

## 一、模块架构

```
人员 C 负责的三个模块，在 Agent 工作流中的位置：

user question
     │
     ▼
┌─────────────┐
│ intent_node │──▶ thinking_service.add_thinking_step("intent", ...)
└──────┬──────┘
       │
       ▼
┌───────────────┐
│ retrieve_node │──▶ thinking_service.add_thinking_step("retrieve", ...)
└───────┬───────┘
        │
        ▼
┌─────────────┐
│ rerank_node │──▶ thinking_service.add_thinking_step("rerank", ...)
└──────┬──────┘
       │
       ▼
┌────────────────┐
│ generate_node  │──▶ context.build_context(history_messages)      ← 拼装多轮上下文
│                │──▶ thinking_service.add_thinking_step("generate")
│                │──▶ citation_service.build_citations(docs)       ← 构建引用数组
│                │──▶ citation_service.merge_consecutive()         ← 合并同文档引用
└────────────────┘
       │
       ▼
  SSE 推送到前端:
    ┌──────────┐  ┌──────────┐  ┌──────────┐
    │ thinking │  │ message  │  │ citation │
    │ 事件流    │  │ 流式正文  │  │ 引用列表  │
    └──────────┘  └──────────┘  └──────────┘
```

---

## 二、各模块原理

### 2.1 上下文管理 (`graph/context.py`)

**问题:** LLM 上下文窗口有限（DeepSeek 默认 8K tokens），多轮对话历史越来越长，必须截断。

**策略:**
1. Token 估算：中文字符 ×1.5 + 英文单词 ×1.0 → 比字符数计更精确
2. 从最新消息开始保留，逐条累加 token 直到超限
3. 丢弃最早的消息，始终保留 system prompt + 当前问题

```
固定开销（system + 当前问题）
     ↓
可用来放历史的 token = 8192 - 固定开销 - 200(余量)
     ↓
从最新→最旧：逐条加，超了停止
     ↓
"助手: 上次的回答"      ← 保留（最新）
"用户: 还有呢"           ← 保留
"[更早的对话内容已被截断]" ← 提示被截断了
```

### 2.2 思考过程 (`service/thinking_service.py`)

**问题:** 用户不知道 AI 在做什么，等待时焦虑。

**方案:** 工作流每个节点产出一个思考步骤，通过 SSE 实时推送。

```
intent  → [意图识别] 识别意图: KNOWLEDGE_QA          (15ms)
retrieve → [检索] 检索到 5 条相关片段                (320ms)
rerank  → [重排序] 重排序后保留 3 条片段             (12ms)
generate → [生成] 回答生成完成                       (2100ms)
────────────────────────────────────────────────
         总耗时: 2447ms
```

前端收到 `type=thinking` 的 SSE 事件，实时渲染进度条。思考完成后自动折叠。

### 2.3 引用溯源 (`service/citation_service.py`)

**问题:** 需求 3.4 要求回答中标明信息来源，支持悬浮预览和原文下载。

**方案:** 四步处理：

```
retrieved_docs（检索结果）
     │
     ▼ build_citations()
[{index:1, doc_id, doc_name, snippet, score}, ...]   ← 标准引用数组
     │
     ▼ merge_consecutive_citations()
[{indices:[1,2], doc_id:"d1"}, {index:3, doc_id:"d2"}] ← 同文档连续合并
     │
     ▼ insert_citation_marks()
"回答正文...\n\n[1] 规程A.pdf\n[3] 规程B.pdf"          ← 追加引用列表
```

**合并规则:** 连续多条引用指向同一文档 → 合并为一条，indices 变为数组。

---

## 三、文件清单

| 文件 | 行数 | 状态 |
|------|:---:|:---:|
| `qa-agent/core/constants.py` | 26 | ✅ SSE 事件 + 思考步骤类型 |
| `qa-agent/graph/context.py` | 122 | ✅ 上下文拼装 + Token 截断 |
| `qa-agent/service/thinking_service.py` | 118 | ✅ 思考步骤 + SSE 事件转换 |
| `qa-agent/service/citation_service.py` | 160 | ✅ 引用构建 + 提取 + 合并 |
| `qa-agent/tests/test_chat.py` | 198 | ✅ 25 个测试全通过 |

---

## 四、公共接口（给 A 和 B 调用的契约）

### A 的 `nodes.py` 改造时用：

```python
# 替换旧的 _append_step()
from qa_agent.service.thinking_service import add_thinking_step
steps = add_thinking_step(state.get("thinking_steps") or [], "intent", f"识别意图: {intent}")

# 替换旧的 citations 构建
from qa_agent.service.citation_service import build_citations, merge_consecutive_citations
citations = build_citations(documents)
merged = merge_consecutive_citations(citations)

# 在 generate_node 里加入多轮上下文
from qa_agent.graph.context import build_context
history = build_context(state["messages"], max_tokens=8192)
```

### B 的 `chat.py` SSE 推送时用：

```python
from qa_agent.service.thinking_service import to_sse_event
yield f"data: {json.dumps(to_sse_event(step))}\n\n"

from qa_agent.service.citation_service import citation_to_sse
yield f"data: {json.dumps(citation_to_sse(merged_citations))}\n\n"
```

---

## 五、测试

```bash
cd smart-km-report-gen
PYTHONPATH="qa-agent" python -m pytest qa-agent/tests/test_chat.py -v
```

```
TestContext::test_estimate_tokens_chinese      PASSED
TestContext::test_build_context_basic          PASSED
TestContext::test_truncate_by_tokens_keeps_recent PASSED
TestThinkingService::test_add_multiple_steps    PASSED
TestCitationService::test_merge_consecutive_same_doc PASSED
... 25 passed in 0.04s
```

---

## 六、如何在 feat/b-context 分支提交

按 Git 防冲突方案，你只改这五个文件：

```bash
git add qa-agent/core/constants.py
git add qa-agent/graph/context.py
git add qa-agent/service/thinking_service.py
git add qa-agent/service/citation_service.py
git add qa-agent/tests/test_chat.py
git commit -m "feat(b): 多轮上下文管理 + 思考过程 + 引用溯源"
```

**绝对不碰:**
- `graph/state.py, nodes.py, workflow.py`（A 的）
- `api/chat.py, api/conversation.py, db/models.py`（B 的）
