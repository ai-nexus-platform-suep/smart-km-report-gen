# qa-agent 智能体接口说明

## 1. 文档范围

本文档面向后端、前端、知识库模块和测试同学，说明 `qa-agent` 合并后的对接边界。完整 HTTP 字段、curl 和 SSE 示例见 `qa-agent/API.md`，工作流图和节点细节见 `qa-agent/AGENT_WORKFLOW.md`。

当前 `qa-agent` 已提供：

- LangGraph 智能问答编排。
- 对话管理 REST API。
- `POST /api/chat` SSE 流式聊天。
- `POST /api/chat/test` 非持久化 Agent 测试接口。
- 多轮上下文、思考过程、引用溯源规范结构。

当前仍不执行：报告生成、知识库 CRUD、任务动作。相关意图只识别并进入澄清路径。

## 2. Agent Graph 调用接口

内部调用方可直接使用 `agent_graph`：

```python
from qa_agent.graph.workflow import agent_graph

result = await agent_graph.ainvoke({
    "messages": [
        {"role": "user", "content": "什么是技术监督？"},
    ],
    "question": "什么是技术监督？",
    "user_id": 1001,
    "conversation_id": 2001,
    "selected_kb_ids": [1, 2],
})
```

流式聊天接口使用：

```python
async for mode, chunk in agent_graph.astream(
    agent_input,
    stream_mode=["updates", "custom"],
):
    ...
```

其中 `custom` 模式承载 token delta，`updates` 模式承载节点状态更新。

## 3. 输入 State Schema

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `messages` | `list[Any]` | 是 | 历史消息，支持 `{"role":"user","content":"..."}`。 |
| `question` | `str` | 建议 | 当前用户问题；为空时工作流尝试从最后一条消息读取。 |
| `user_id` | `int` | 否 | 用户 ID。 |
| `conversation_id` | `int` | 否 | 会话 ID。 |
| `selected_kb_ids` | `list[int]` | 否 | 用户选择的知识库范围。 |

最小输入：

```json
{
  "messages": [{"role": "user", "content": "你好"}],
  "question": "你好"
}
```

## 4. 输出 State Schema

| 字段 | 类型 | 说明 |
|---|---|---|
| `question` | `str` | 实际处理的问题文本。 |
| `intent` | `str` | 识别出的意图。 |
| `intent_confidence` | `float` | 意图识别置信度。 |
| `route_reason` | `str` | 分类和路由原因。 |
| `classification_source` | `str` | `rule`、`llm`、`fallback`。 |
| `needs_clarification` | `bool` | 是否需要澄清。 |
| `mode` | `str` | `direct`、`rag`、`clarify`。 |
| `retrieved_docs` | `list[dict]` | 标准化检索片段。 |
| `thinking_steps` | `list[dict]` | 规范思考步骤。 |
| `citations` | `list[dict]` | 规范引用来源。 |
| `final_response` | `str` | 最终回答或澄清内容。 |
| `error` | `str | None` | 受控错误信息。 |

## 5. 意图与路由

| `intent` | `mode` | 是否检索 | 当前行为 |
|---|---|---|---|
| `CHAT` | `direct` | 否 | 直接回答。 |
| `KNOWLEDGE_QA` | `rag` 或 `clarify` | 视情况 | 明确知识问答进入 RAG；低置信度时先澄清。 |
| `DOCUMENT_SEARCH` | `rag` | 是 | 检索、重排序后生成回答。 |
| `REPORT_GENERATION` | `clarify` | 否 | 只识别，不执行报告生成。 |
| `KB_MANAGEMENT` | `clarify` | 否 | 只识别，不执行知识库 CRUD。 |
| `TASK_ACTION` | `clarify` | 否 | 只识别，不执行任务动作。 |

## 6. Thinking Steps 合同

工作流内部存储：

```json
{
  "type": "retrieve",
  "message": "检索到 5 条片段",
  "timestamp": 1782734400.0,
  "elapsed_ms": 320
}
```

SSE `thinking` 输出：

```json
{
  "type": "thinking",
  "step_type": "retrieve",
  "message": "检索到 5 条片段",
  "elapsed_ms": 320
}
```

步骤类型包括 `intent`、`clarify`、`retrieve`、`rerank`、`generate`、`citation`。

## 7. Citations 合同

生成节点基于检索文档构造引用，并合并连续同文档引用：

```json
[
  {
    "index": 1,
    "indices": [1, 2],
    "doc_id": "doc_001",
    "doc_name": "技术监督管理办法.pdf",
    "chapter_path": "",
    "snippet": "技术监督是指...",
    "full_snippet": "技术监督是指...",
    "score": 0.92,
    "chunk_type": "正文"
  }
]
```

SSE `citation` 输出：

```json
{
  "type": "citation",
  "citations": [],
  "merged": true
}
```

## 8. HTTP/SSE 接口摘要

| 方法 | 路径 | 说明 |
|---|---|---|
| `GET` | `/api/conversations` | 查询会话列表。 |
| `POST` | `/api/conversations` | 创建会话。 |
| `PATCH` | `/api/conversations/{conversation_id}` | 修改会话标题。 |
| `DELETE` | `/api/conversations/{conversation_id}` | 软删除会话及消息。 |
| `GET` | `/api/conversations/{conversation_id}/messages` | 查询会话消息。 |
| `POST` | `/api/chat/test` | 非持久化测试 Agent 图。 |
| `POST` | `/api/chat` | SSE 流式聊天。 |

`POST /api/chat` 会输出 `thinking`、`message`、`citation`、`error`、`done` 事件。`message` 事件包含 token 增量，最终会发送 `finished=true` 的确认事件。

## 9. 知识库检索适配器契约

配置 `KNOWLEDGE_SEARCH_URL` 后，`retrieve_node` 调用知识库 HTTP 接口：

```json
{
  "query": "用户问题",
  "selected_kb_ids": [1, 2],
  "top_k": 5,
  "similarity_threshold": 0.7,
  "embedding": null
}
```

知识库接口可以返回文档数组，或返回包含 `documents`、`data`、`items`、`results` 任一字段的包装对象。Agent 会标准化为：

| 字段 | 类型 | 说明 |
|---|---|---|
| `doc_id` | `str` | 文档 ID，兼容 `docId`。 |
| `doc_name` | `str` | 文档名称，兼容 `docName`。 |
| `kb_id` | `int | None` | 知识库 ID，兼容 `kbId`。 |
| `snippet` | `str` | 命中文本，兼容 `content`。 |
| `score` | `float` | 相似度分数，兼容 `similarity`。 |
| `metadata` | `dict` | 额外元数据。 |

未配置知识库地址、请求失败、返回结构不合法时，适配器返回空数组，不抛出异常。

## 10. 降级与错误行为

| 场景 | 当前行为 |
|---|---|
| 未配置 `LLM_API_KEY` | 返回受控配置提示，图不崩溃。 |
| LLM HTTP 调用失败 | 返回“LLM 服务暂不可用，请稍后重试。”。 |
| LLM 分类失败或非法 JSON | 分类来源置为 `fallback`，进入澄清路径。 |
| 未配置 `KNOWLEDGE_SEARCH_URL` | 检索结果为空，RAG 走无知识库降级生成。 |
| RAG 检索为空 | 回答前明确包含“未找到相关知识库信息”。 |
| `/api/chat` 会话不存在 | SSE `error` 后跟 `done`。 |
| 数据库会话初始化失败 | SSE `error` 后跟 `done`。 |

## 11. 本地验证命令

```bash
python -m pytest qa-agent/tests/test_chat.py -v
python -m compileall qa-agent qa_agent
```

当前合并验证结果：`32 passed`，编译检查通过。
