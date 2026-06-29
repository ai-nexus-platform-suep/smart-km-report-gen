# qa-agent 智能体接口文档

## 1. 文档范围

本文档面向人员 B、人员 C、知识库模块同学，说明当前 `qa-agent` 可解释路由与 LangGraph 工作流的对接接口。

当前 `qa-agent` 只提供智能问答编排能力：意图识别、澄清、知识库检索适配、重排序、回答生成。报告生成、知识库 CRUD、任务执行、SSE 接口实现不在当前接口范围内。

## 2. Agent Graph 调用接口

人员 B 或其他调用方通过 `agent_graph` 调用工作流：

```python
from qa_agent.graph.workflow import agent_graph

result = await agent_graph.ainvoke({
    "messages": [{"role": "user", "content": "什么是技术监督？"}],
    "question": "什么是技术监督？",
    "user_id": 1001,
    "conversation_id": 2001,
    "selected_kb_ids": [1, 2],
})
```

同步测试或脚本中可以使用：

```python
import asyncio
from qa_agent.graph.workflow import agent_graph

result = asyncio.run(agent_graph.ainvoke({
    "messages": [{"role": "user", "content": "你好"}],
    "question": "你好",
}))
```

当前 B 侧 `qa-agent/api/chat.py` 仍是占位文件，HTTP `/chat` 与 SSE 事件格式以后由人员 B 封装；本文件只约定图调用与状态字段。

## 3. 输入 State Schema

调用 `agent_graph.ainvoke()` 时传入 `dict`，字段如下：

| 字段 | 类型 | 必填 | 说明 |
|---|---:|:---:|---|
| `messages` | `list[Any]` | 是 | LangGraph 消息列表，支持 `{"role": "user", "content": "..."}` 形式；后续可由 B/C 扩展历史消息。 |
| `question` | `str` | 建议 | 当前用户问题。若为空，工作流会尝试从 `messages` 最后一条消息读取 `content`。 |
| `user_id` | `int` | 否 | 用户 ID，当前图内部不强依赖，预留给 B/C 和审计。 |
| `conversation_id` | `int` | 否 | 会话 ID，当前图内部不强依赖，预留给上下文和消息落库。 |
| `selected_kb_ids` | `list[int]` | 否 | 用户选择的知识库范围；为空时由知识库检索接口自行决定默认范围。 |

最小可用输入：

```json
{
  "messages": [{"role": "user", "content": "你好"}],
  "question": "你好"
}
```

## 4. 输出 State Schema

工作流返回完整状态，调用方重点消费以下字段：

| 字段 | 类型 | 说明 |
|---|---:|---|
| `question` | `str` | 实际处理的问题文本。 |
| `intent` | `str` | 识别出的意图，取值见“意图值”。 |
| `intent_confidence` | `float` | 意图识别置信度，范围 `0.0` 到 `1.0`。 |
| `route_reason` | `str` | 可展示或用于排查误判的分类/路由原因。 |
| `classification_source` | `str` | 分类来源：`rule`、`llm`、`fallback`。 |
| `needs_clarification` | `bool` | 是否需要先追问或说明能力边界。 |
| `mode` | `str` | 路由模式：`direct`、`rag`、`clarify`。 |
| `retrieved_docs` | `list[dict]` | 标准化后的检索文档片段。直答、澄清或空检索时通常为空数组。 |
| `thinking_steps` | `list[dict]` | 思考过程步骤，供 B 侧展示或 C 侧增强。 |
| `citations` | `list[dict]` | 回答引用来源；仅有检索片段时生成。 |
| `final_response` | `str` | 最终给用户的回答或澄清内容。 |
| `error` | `str | None` | 受控错误信息；当前节点通常用 `None` 表示未发生图级错误。 |

## 5. 意图值与路由语义

| `intent` | `mode` | 是否检索 | 当前行为 |
|---|---|:---:|---|
| `CHAT` | `direct` | 否 | 直接进入回答生成节点。 |
| `KNOWLEDGE_QA` | `rag` 或 `clarify` | 视情况 | 明确知识问答进入 RAG；低置信度或上下文不足时先澄清。 |
| `DOCUMENT_SEARCH` | `rag` | 是 | 调用知识库检索适配器，再重排序和生成回答。 |
| `REPORT_GENERATION` | `clarify` | 否 | 当前只识别，不执行报告生成，返回能力边界说明。 |
| `KB_MANAGEMENT` | `clarify` | 否 | 当前只识别，不执行知识库 CRUD，返回能力边界说明。 |
| `TASK_ACTION` | `clarify` | 否 | 当前只识别，不执行任务动作，返回能力边界说明。 |

路由规则：

1. `intent_node` 先用高确定性规则分类。
2. 规则无法判断时，若配置了 `LLM_API_KEY`，尝试调用 OpenAI-compatible Chat Completions 做 JSON 结构化分类。
3. LLM 不可用、调用失败或返回非法 JSON 时，进入 `fallback` 分类来源，并走 `clarify`。
4. `needs_clarification=true` 时，不进入检索链路。
5. `REPORT_GENERATION`、`KB_MANAGEMENT`、`TASK_ACTION` 当前永远不触发 RAG。

## 6. Thinking Steps 格式

`thinking_steps` 是追加式数组，每个节点通过以下结构写入一条可展示步骤：

```json
{
  "event_type": "intent",
  "message": "识别意图: KNOWLEDGE_QA，置信度 0.80，来源 rule。命中知识问答表达。"
}
```

当前可能的 `event_type`：

| `event_type` | 产生节点 | 说明 |
|---|---|---|
| `intent` | `intent_node` | 意图、置信度、来源和路由原因。 |
| `clarify` | `clarify_node` | 说明进入澄清或能力边界路径。 |
| `retrieve` | `retrieve_node` | 检索到的相关片段数量。 |
| `rerank` | `rerank_node` | 重排序后保留的片段数量。 |
| `generate` | `generate_node` | 回答生成完成。 |

人员 C 后续可以在自有 `thinking_service.py` 中增强展示格式，但建议保留 `event_type` 和 `message` 基础字段，避免破坏 B 侧消费。

## 7. Citations 格式

当 `generate_node` 基于 `retrieved_docs` 生成回答时，会按文档顺序生成 `citations`：

```json
[
  {
    "index": 1,
    "doc_id": "doc_001",
    "doc_name": "技术监督管理办法.pdf",
    "snippet": "技术监督是指...",
    "score": 0.92
  }
]
```

字段说明：

| 字段 | 类型 | 说明 |
|---|---:|---|
| `index` | `int` | 当前回答中的引用序号，从 `1` 开始。 |
| `doc_id` | `str` | 文档唯一 ID，由知识库模块提供。 |
| `doc_name` | `str` | 文档名称。 |
| `snippet` | `str` | 命中的文本片段。 |
| `score` | `float` | 检索或重排序分数。 |

当前引用格式是基础版本，人员 C 后续可以在自有 `citation_service.py` 中补充页码、段落号、下载链接等元数据。

## 8. 知识库检索适配器契约

配置 `KNOWLEDGE_SEARCH_URL` 后，`retrieve_node` 会调用：

```python
from qa_agent.client.knowledge_client import search_knowledge

documents = await search_knowledge(
    query="用户问题",
    selected_kb_ids=[1, 2],
    top_k=5,
    similarity_threshold=0.7,
    embedding=None,
)
```

HTTP 请求方法：`POST`

HTTP 请求体：

```json
{
  "query": "用户问题",
  "selected_kb_ids": [1, 2],
  "top_k": 5,
  "similarity_threshold": 0.7,
  "embedding": null
}
```

知识库接口可以直接返回文档数组：

```json
[
  {
    "doc_id": "doc_001",
    "doc_name": "技术监督管理办法.pdf",
    "kb_id": 1,
    "snippet": "技术监督是指...",
    "score": 0.92,
    "metadata": {"page": 3}
  }
]
```

也可以返回包装对象，Agent 会识别 `documents`、`data`、`items`、`results`：

```json
{
  "documents": [
    {
      "doc_id": "doc_001",
      "doc_name": "技术监督管理办法.pdf",
      "kb_id": 1,
      "snippet": "技术监督是指...",
      "score": 0.92,
      "metadata": {"page": 3}
    }
  ]
}
```

标准化后的 `retrieved_docs` 字段：

| 字段 | 类型 | 必填 | 说明 |
|---|---:|:---:|---|
| `doc_id` | `str` | 是 | 文档 ID；兼容输入字段 `doc_id` / `docId`。 |
| `doc_name` | `str` | 是 | 文档名称；兼容输入字段 `doc_name` / `docName`。 |
| `kb_id` | `int | None` | 否 | 知识库 ID；兼容输入字段 `kb_id` / `kbId`。 |
| `snippet` | `str` | 是 | 命中文本；兼容输入字段 `snippet` / `content`。 |
| `score` | `float` | 是 | 相似度分数；兼容输入字段 `score` / `similarity`。 |
| `metadata` | `dict` | 否 | 额外元数据，非对象时自动置为空对象。 |

如果未配置 `KNOWLEDGE_SEARCH_URL`、问题为空、HTTP 调用失败、返回非 JSON 或返回结构不合法，适配器返回空数组，不抛出异常。

## 9. 澄清与能力边界行为

以下情况进入 `clarify_node`：

- 用户问题为空或缺少必要上下文。
- 分类置信度低于阈值。
- LLM 分类不可用且规则无法可靠分类。
- 命中 `REPORT_GENERATION`、`KB_MANAGEMENT`、`TASK_ACTION` 这类当前未开放执行能力的意图。

普通澄清响应示例：

```text
我还需要更多信息才能准确处理。请求缺少材料、目标或输出要求，需要先澄清。请补充要分析的材料、具体问题或期望输出。
```

未开放能力响应示例：

```text
我已识别到这是 REPORT_GENERATION 类型请求。当前 Agent 迭代只支持问答和文档检索编排，暂不直接执行该操作。请补充目标、材料范围和期望输出，我可以先帮你澄清需求或转成可问答的问题。
```

## 10. 错误与降级行为

| 场景 | 当前行为 |
|---|---|
| 未配置 `LLM_API_KEY` | 回答生成返回“LLM API key 未配置，当前仅完成 Agent 工作流编排。”，图不崩溃。 |
| LLM HTTP 调用失败 | 回答生成返回“LLM 服务暂不可用，请稍后重试。”。 |
| LLM 返回空 choices | 回答生成返回“LLM 服务未返回有效回答。”。 |
| LLM 分类失败或非法 JSON | 分类来源置为 `fallback`，进入澄清路径。 |
| 未配置 `KNOWLEDGE_SEARCH_URL` | 检索结果为空，RAG 问题进入无知识库降级生成。 |
| 知识库 HTTP 调用失败 | 检索结果为空，不抛出异常。 |
| RAG 检索为空 | `generate_node` 会在回答前明确包含“未找到相关知识库信息”。 |

接口中不要传递、记录或展示真实 API Key。`.env` 只在本地配置中使用，不能进入 `AgentState`。

## 11. 示例响应

### 11.1 闲聊直答

输入：

```json
{
  "messages": [{"role": "user", "content": "你好，请介绍一下你自己"}],
  "question": "你好，请介绍一下你自己"
}
```

关键输出：

```json
{
  "intent": "CHAT",
  "intent_confidence": 0.95,
  "classification_source": "rule",
  "mode": "direct",
  "needs_clarification": false,
  "retrieved_docs": [],
  "citations": [],
  "final_response": "LLM API key 未配置，当前仅完成 Agent 工作流编排。"
}
```

### 11.2 知识问答空检索降级

输入：

```json
{
  "messages": [{"role": "user", "content": "什么是电力技术监督？"}],
  "question": "什么是电力技术监督？",
  "selected_kb_ids": [1]
}
```

关键输出：

```json
{
  "intent": "KNOWLEDGE_QA",
  "mode": "rag",
  "retrieved_docs": [],
  "final_response": "未找到相关知识库信息。LLM API key 未配置，当前仅完成 Agent 工作流编排。"
}
```

### 11.3 模糊请求澄清

输入：

```json
{
  "messages": [{"role": "user", "content": "帮我分析一下"}],
  "question": "帮我分析一下"
}
```

关键输出：

```json
{
  "intent": "KNOWLEDGE_QA",
  "intent_confidence": 0.35,
  "classification_source": "rule",
  "mode": "clarify",
  "needs_clarification": true,
  "retrieved_docs": [],
  "final_response": "我还需要更多信息才能准确处理。请求缺少材料、目标或输出要求，需要先澄清。请补充要分析的材料、具体问题或期望输出。"
}
```

### 11.4 未开放能力边界说明

输入：

```json
{
  "messages": [{"role": "user", "content": "请生成一份技术监督月报"}],
  "question": "请生成一份技术监督月报"
}
```

关键输出：

```json
{
  "intent": "REPORT_GENERATION",
  "mode": "clarify",
  "needs_clarification": true,
  "retrieved_docs": [],
  "final_response": "我已识别到这是 REPORT_GENERATION 类型请求。当前 Agent 迭代只支持问答和文档检索编排，暂不直接执行该操作。请补充目标、材料范围和期望输出，我可以先帮你澄清需求或转成可问答的问题。"
}
```

## 12. 本地验证命令

在项目根目录运行：

```bash
python -m compileall qa-agent
```

验证图可导入和编译：

```bash
python -c "from qa_agent.graph.workflow import agent_graph; print(agent_graph.get_graph())"
```

验证直答路径：

```bash
python -c "import asyncio; from qa_agent.graph.workflow import agent_graph; result = asyncio.run(agent_graph.ainvoke({'messages': [{'role': 'user', 'content': '你好'}], 'question': '你好'})); print(result['intent'], result['mode'], result['final_response'])"
```

验证澄清路径：

```bash
python -c "import asyncio; from qa_agent.graph.workflow import agent_graph; result = asyncio.run(agent_graph.ainvoke({'messages': [{'role': 'user', 'content': '帮我分析一下'}], 'question': '帮我分析一下'})); print(result['intent'], result['mode'], result['final_response'])"
```

验证 RAG 空检索降级路径：

```bash
python -c "import asyncio; from qa_agent.graph.workflow import agent_graph; result = asyncio.run(agent_graph.ainvoke({'messages': [{'role': 'user', 'content': '什么是技术监督？'}], 'question': '什么是技术监督？', 'selected_kb_ids': [1]})); print(result['intent'], result['mode'], result['retrieved_docs'], result['final_response'])"
```

这些命令不需要真实知识库服务。未配置 `LLM_API_KEY` 时，回答内容会是受控配置提示，这是预期行为。
