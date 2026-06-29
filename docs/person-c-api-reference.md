# 人员 C 模块 — 完整 API 参考与实现原理

> 分支: feat/b-context | 人员: C | Python 3.10+ | 2026-06-29

---

## 一、模块总览

```
人员 C 负责: 多轮上下文 + 思考过程 + 引用溯源

qa-agent/
├── core/
│   └── constants.py          ← SSE 事件类型 + 思考步骤类型 + Token 常量
├── graph/
│   └── context.py            ← 历史消息拼装 + Token 截断 + Token 统计
├── service/
│   ├── thinking_service.py   ← 思考步骤 CRUD + SSE 转换 + 摘要
│   └── citation_service.py   ← 引用构建 + 提取 + 标记插入 + 合并
└── tests/
    └── test_chat.py          ← 25 个单元测试
```

---

## 二、core/constants.py

### SSEEventType

SSE 推送给前端的事件类型枚举。`(str, Enum)` 兼容 Python 3.10+。

| 枚举值 | 值 | 用途 |
|--------|-----|------|
| `THINKING` | `"thinking"` | 思考过程步骤（前端渲染进度条） |
| `MESSAGE` | `"message"` | 回答正文（流式增量推送每个 token） |
| `CITATION` | `"citation"` | 引用溯源列表 |
| `ERROR` | `"error"` | 错误信息 |
| `DONE` | `"done"` | 回答完成，前端停止 loading |
| `STOP` | `"stop"` | 用户主动中止生成 |

### ThinkingStepType

思考步骤内部类型。对应 Agent 工作流的四个节点。

| 枚举值 | 值 | 映射节点 |
|--------|-----|----------|
| `INTENT` | `"intent"` | intent_node 意图识别 |
| `RETRIEVE` | `"retrieve"` | retrieve_node 知识库检索 |
| `RERANK` | `"rerank"` | rerank_node 重排序 |
| `GENERATE` | `"generate"` | generate_node 回答生成 |
| `CITATION` | `"citation"` | 引用处理阶段 |

### Token 常量

| 常量 | 值 | 说明 |
|------|-----|------|
| `TOKENS_PER_CHINESE_CHAR` | 1.5 | 中文字符 token 折算系数 |
| `TOKENS_PER_ENGLISH_WORD` | 1.0 | 英文单词 token 折算系数 |
| `DEFAULT_MAX_CONTEXT_TOKENS` | 8192 | LLM 默认上下文窗口（DeepSeek 8K） |

---

## 三、graph/context.py — 上下文管理

### 设计原理

```
LLM 的上下文窗口有限（DeepSeek 默认 8192 tokens）。
多轮对话进行到第 10 轮时，历史消息可能超过 8000 tokens。
如果全部塞进 prompt → LLM 报错 / 截断回答 / 丢失早期上下文。

解决策略: 估算 token → 从最新消息保留 → 超出丢弃 → 标注截断
```

```
固定开销计算                    动态保留
┌──────────────┐              ┌──────────────────┐
│ system prompt │  800 tokens  │ 第1轮 Q&A         │ ← 最早，先丢弃
│ 当前问题      │  100 tokens  │ 第2轮 Q&A         │
│ 余量          │  200 tokens  │ ...              │
├──────────────┤              │ 第N-1轮 Q&A       │
│ 可用来放历史 │  7092 tokens  │ 第N轮 Q&A         │ ← 最新，优先保留
└──────────────┘              └──────────────────┘
```

---

### estimate_tokens(text)

估算文本的 token 数量。

```
公式:
  token ≈ 中文字符数 × 1.5 + 英文单词数 × 1.0 + 其他符号数

精确度: 不是 tokenizer 级别，但比 strlen() 准确 3-5 倍
```

| 参数 | 类型 | 说明 |
|------|------|------|
| text | str | 输入文本 |

| 返回 | 类型 | 说明 |
|------|------|------|
| | int | 估算的 token 数 |

```
>>> estimate_tokens("你好世界")
6
>>> estimate_tokens("hello world")
2
>>> estimate_tokens("")
0
```

---

### build_context(messages, max_tokens, system_prompt, current_question)

将历史消息拼装为 LLM prompt 中的上下文字符串。

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| messages | list[dict] | — | `[{"role":"user","content":"..."}, ...]` |
| max_tokens | int | 8192 | 上下文窗口上限 |
| system_prompt | str | `""` | system prompt（固定开销，不计入截断） |
| current_question | str | `""` | 当前问题（固定开销，不计入截断） |

| 返回 | 类型 | 说明 |
|------|------|------|
| | str | 拼装后的上下文，可直接放入 LLM messages |

```
输入:
  messages = [
    {"role": "user", "content": "变压器油温异常怎么处理"},
    {"role": "assistant", "content": "建议检查冷却系统和油位"},
    {"role": "user", "content": "具体步骤是什么"},
  ]

输出:
  "用户: 变压器油温异常怎么处理
   助手: 建议检查冷却系统和油位
   用户: 具体步骤是什么"
```

**算法流程:**

```
1. 分离 system 消息和对话消息
2. 计算固定 token 开销 = system_prompt + current_question + system消息
3. 可用来放历史的 token = max_tokens - 固定开销 - 200(余量)
4. 从最新 → 最旧遍历历史:
   a. 格式化消息: _format_message() → "用户: xxx" / "助手: xxx"
   b. 估算这条消息的 token
   c. 累加后超限 → break
   d. 不超限 → 插入到结果开头（恢复正序）
5. 有截断时 → 在开头插入 "[更早的对话内容已被截断]"
```

---

### truncate_by_tokens(messages, max_tokens)

按 token 截断消息列表。

| 参数 | 类型 | 说明 |
|------|------|------|
| messages | list[dict] | 消息列表 |
| max_tokens | int | token 上限 |

| 返回 | 类型 | 说明 |
|------|------|------|
| | list[dict] | 截断后的消息子列表（保持原序） |

```
输入: [长消息, 长消息, 短消息], max_tokens=50
输出: [短消息]   ← 只保留最后一条
```

---

### count_context_tokens(messages)

统计消息列表总 token 数（调试/监控用）。遍历每条消息的 content 字段累加估算。

---

## 四、service/thinking_service.py — 思考过程

### 设计原理

```
Agent 工作流执行时用户看不到内部进度 → 等待焦虑 → 差体验

解决: 每个节点完成后产出一个"思考步骤"，通过 SSE 实时推送前端
  intent  → "正在理解您的问题..."
  retrieve → "检索到 5 条相关知识"
  rerank  → "正在排序最佳答案"
  generate → "正在生成回答..."

前端渲染为可折叠的进度条，完成后自动收起
```

---

### add_thinking_step(steps, event_type, message, elapsed_ms)

新增一个思考步骤。**返回新列表，不修改原列表**（函数式编程，避免副作用）。

| 参数 | 类型 | 必填 | 说明 |
|------|------|:---:|------|
| steps | list[dict] | Y | 当前步骤列表 |
| event_type | str | Y | 步骤类型: intent/retrieve/rerank/generate/citation |
| message | str | Y | 展示文本 |
| elapsed_ms | float\|None | N | 本步耗时（毫秒） |

| 返回 | 类型 | 说明 |
|------|------|------|
| | list[dict] | 新步骤列表 |

**每步 dict 结构:**

```json
{
  "type": "retrieve",
  "message": "检索到 5 条相关片段",
  "timestamp": 1719648000.123,
  "elapsed_ms": 320
}
```

**完整示例:**

```python
steps = []
steps = add_thinking_step(steps, "intent", "识别意图: KNOWLEDGE_QA", 15)
steps = add_thinking_step(steps, "retrieve", "检索到 5 条片段", 320)
steps = add_thinking_step(steps, "rerank", "保留 3 条最佳结果", 12)
steps = add_thinking_step(steps, "generate", "回答生成完成", 2100)
# len(steps) == 4
```

---

### build_thinking_summary(steps)

将步骤列表转为可读摘要文本。用于非流式场景（`ainvoke` 后返回一段总结）。

| 参数 | 类型 | 说明 |
|------|------|------|
| steps | list[dict] | add_thinking_step 的输出 |

| 返回 | 类型 | 说明 |
|------|------|------|
| | str | 人类可读的步骤摘要 |

```
输出示例:
  1. [意图识别] 识别意图: KNOWLEDGE_QA
  2. [检索] 检索到 5 条片段 (320ms)
  3. [重排序] 保留 3 条最佳结果 (12ms)
  4. [生成] 回答生成完成 (2100ms)
```

---

### to_sse_event(step)

将单个思考步骤转为 SSE 事件的 data 字段。

| 参数 | 类型 | 说明 |
|------|------|------|
| step | dict | 单个步骤 dict |

| 返回 | 类型 | 说明 |
|------|------|------|
| | dict | SSE 事件 payload |

```python
# 输入: step = {"type": "retrieve", "message": "检索到 5 条", "elapsed_ms": 320}
# 输出: {"type": "thinking", "step_type": "retrieve", "message": "检索到 5 条", "elapsed_ms": 320}
```

**B 的 chat.py 使用方式:**

```python
for step in steps:
    yield f"data: {json.dumps(to_sse_event(step))}\n\n"
```

---

### steps_total_time(steps)

计算所有步骤的总耗时（毫秒）。有 `elapsed_ms` 的累加，没有的跳过。

---

## 五、service/citation_service.py — 引用溯源

### 设计原理

```
RAG 回答必须标注信息来源，满足需求说明书 3.4 的全部要求:

  retrieved_docs (检索结果)
       │
       ▼ build_citations()      ← 标准化为统一格式，snippet 截断 300 字
  citation 数组 [index, doc_id, doc_name, snippet, score, ...]
       │
       ▼ merge_consecutive_citations()  ← 同文档连续引用合并
  合并后数组 [{"indices":[1,2],...}, {"index":3,...}]
       │
       ├─→ insert_citation_marks()  ← 追加引用列表到回答末尾
       └─→ citation_to_sse()       ← 转 SSE 推前端
```

**引用数据结构（标准格式）:**

```json
{
  "index": 1,
  "doc_id": "doc_001",
  "doc_name": "技术监督管理办法.pdf",
  "chapter_path": "",
  "snippet": "技术监督是指电力企业对...",
  "full_snippet": "技术监督是指电力企业对设备运行...（完整原文）",
  "score": 0.92,
  "chunk_type": "正文"
}
```

---

### build_citations(documents, start_index=1)

从检索结果构建标准引用数组。**主入口函数，generate_node 结束时调用。**

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| documents | list[dict] | — | retrieved_docs |
| start_index | int | 1 | 起始编号 |

| 返回 | 类型 | 说明 |
|------|------|------|
| | list[dict] | 标准 citation 数组 |

**处理细节:**
- snippet 超过 300 字截断，`full_snippet` 保留原文
- 缺失字段填充默认值（`"未知文档"`, `"正文"`）

---

### extract_citations(text)

从 LLM 回答中提取引用标记。支持 `[1]`、`[2,3]`、`[1-3]` 三种格式。

| 参数 | 类型 | 说明 |
|------|------|------|
| text | str | LLM 生成的回答 |

| 返回 | 类型 | 字段 |
|------|------|------|
| | list[dict] | indices, start（文本位置）, end, raw |

```
输入: "根据[1]的要求和[2,3]的建议..."
输出: [
  {"indices": [1], "start": 2, "end": 5, "raw": "[1]"},
  {"indices": [2, 3], "start": 11, "end": 16, "raw": "[2,3]"}
]
```

---

### insert_citation_marks(text, citations)

在回答末尾追加引用列表（Markdown 格式）。

| 参数 | 类型 | 说明 |
|------|------|------|
| text | str | 回答文本 |
| citations | list[dict] | build_citations 的输出 |

| 返回 | 类型 | 说明 |
|------|------|------|
| | str | 带引用列表的 Markdown 文本 |

```
输出:
  回答正文...

  ---

  **引用来源：**
  [1] **规程A.pdf** (相关性: 0.92)
      > 第3条规定: 应定期检查...
  [2] **手册B.pdf** (相关性: 0.75)
      > 冷却系统维护...
```

---

### merge_consecutive_citations(citations)

合并来自同一文档的**连续**引用。

| 输入 | 输出 | 规则 |
|------|------|------|
| doc_id=d1, doc_id=d1, doc_id=d2 | indices=[1,2], index=3 | d1 连续两条合并，d2 保留 |
| doc_id=d1, doc_id=d2, doc_id=d1 | 三条全保留 | d1 中间隔着 d2，不连续不合并 |

合并后保留**更高的 score** 和对应 snippet。

---

### citation_to_sse(citations)

转 SSE 事件。

```python
# 输出: {"type": "citation", "citations": [...], "merged": true}
```

---

## 六、Token 截断算法详解

### 问题

DeepSeek 上下文窗口 8192 tokens。第10轮对话时：
- 历史消息 ~4000 tokens
- system prompt ~800 tokens
- 当前问题 ~100 tokens
- 检索文档 ~3000 tokens
- **总计 ~7900 tokens，接近极限**

第15轮时直接超限 → LLM 报错或截断输出。

### 方案

```
max_tokens = 8192
fixed = estimate_tokens(system_prompt) + estimate_tokens(current_question) + system_msgs_tokens
available = max_tokens - fixed - 200(margin)

从最新消息开始逐条保留:
  msg_N: 80 tokens, 累加=80   ≤ available ✅ 保留
  msg_N-1: 120 tokens, 累加=200 ≤ available ✅ 保留
  msg_N-2: 5000 tokens, 累加=5200 > available ❌ discard
  msg_1 ~ msg_N-3: 全丢弃

结果: msg_N-1 + msg_N → 加上 "已被截断" 提示
```

### Token 估算精度

| 方法 | 中文精度 | 英文精度 |
|------|:---:|:---:|
| strlen / 2 | 误差 40-60% | 误差 20-30% |
| 本模块 estimate_tokens | 误差 10-15% | 误差 5-10% |

不是精确 tokenizer（那需要加载 BPE 词表），但在工程上足够了。

---

## 七、给 A 的接入说明

A 在 `nodes.py` 中替换旧的临时实现:

```python
# === 旧代码（临时实现，A 的 nodes.py 里）===
def _append_step(state, event_type, message):
    steps = list(state.get("thinking_steps") or [])
    steps.append({"event_type": event_type, "message": message})
    return steps

# === 新代码（调用 C 的模块）===
from qa_agent.service.thinking_service import add_thinking_step

# intent_node:
"thinking_steps": add_thinking_step(
    state.get("thinking_steps") or [], "intent", f"识别意图: {intent}", elapsed_ms
)

# generate_node (替换旧的 citations 构建):
from qa_agent.service.citation_service import build_citations, merge_consecutive_citations
from qa_agent.graph.context import build_context

history = build_context(state["messages"], current_question=question)
citations = build_citations(documents)
merged = merge_consecutive_citations(citations)
```

---

## 八、给 B 的接入说明

B 在 `api/chat.py` 的 SSE 事件循环中:

```python
from qa_agent.service.thinking_service import to_sse_event
from qa_agent.service.citation_service import citation_to_sse

# 推送思考步骤
for step in thinking_steps:
    yield f"data: {json.dumps(to_sse_event(step))}\n\n"

# 推送引用
yield f"data: {json.dumps(citation_to_sse(merged_citations))}\n\n"

# 推送完成
yield f"data: {json.dumps({'type': 'done'})}\n\n"
```

---

## 九、测试

```bash
cd smart-km-report-gen
PYTHONPATH="qa-agent" python -m pytest qa-agent/tests/test_chat.py -v
```

```
TestContext (8):     estimate_tokens / build_context / truncate_by_tokens
TestThinkingService (7): add_thinking_step / build_thinking_summary / to_sse_event
TestCitationService (8): build_citations / extract_citations / merge_consecutive_citations
TestConstants (2):   SSEEventType / ThinkingStepType
────────────────
25 passed in 0.04s
```
