# qa-agent FastAPI 接口文档

> 服务：`qa-agent`<br>
> 基础路径：`/api`<br>
> 默认端口：`8000`<br>
> 当前版本：A/B/C 合并后的智能问答服务

## 1. 通用说明

### 1.1 服务地址

```text
http://127.0.0.1:8000/api
```

Swagger 文档：

```text
http://127.0.0.1:8000/docs
```

### 1.2 REST 统一响应格式

除 SSE 流式接口外，REST 接口统一返回：

```json
{
  "code": 0,
  "message": "success",
  "data": {}
}
```

| 字段 | 类型 | 说明 |
|---|---|---|
| `code` | `int` | 业务状态码，`0` 表示成功。 |
| `message` | `string` | 业务提示信息。 |
| `data` | `object | array | null` | 业务数据。 |

### 1.3 HTTP 错误

| 状态码 | 场景 |
|---|---|
| `400` | `question` 为空字符串。 |
| `404` | 会话不存在。 |
| `422` | 请求体或参数校验失败。 |

### 1.4 鉴权与用户

当前开发阶段未接入 JWT。会话接口通过 Query 参数 `user_id` 传入用户 ID，默认值为 `1`；聊天接口可在请求体传入 `user_id`，不传时使用默认用户 ID。

### 1.5 数据表

| 表名 | 说明 |
|---|---|
| `qa_session` | 会话表。 |
| `qa_message` | 消息表。 |

删除会话为软删除，服务端将会话及其关联消息标记为已删除，不做物理删除。

## 2. 枚举

### 2.1 消息角色

| 值 | 说明 |
|---|---|
| `user` | 用户消息。 |
| `assistant` | AI 回复。 |
| `system` | 系统消息。 |

### 2.2 生成状态

| 值 | 说明 |
|---|---|
| `0` | 生成中。 |
| `1` | 已完成。 |
| `2` | 失败。 |

### 2.3 意图类型

| 值 | 当前行为 |
|---|---|
| `CHAT` | 直接回答。 |
| `KNOWLEDGE_QA` | 知识问答，进入 RAG 或澄清。 |
| `DOCUMENT_SEARCH` | 文档检索，进入 RAG。 |
| `REPORT_GENERATION` | 当前只识别并澄清，不执行报告生成。 |
| `KB_MANAGEMENT` | 当前只识别并澄清，不执行知识库 CRUD。 |
| `TASK_ACTION` | 当前只识别并澄清，不执行任务动作。 |

### 2.4 SSE 事件类型

| 事件 | 说明 |
|---|---|
| `thinking` | 思考过程步骤。 |
| `message` | 回答内容流式增量或最终确认。 |
| `citation` | 合并后的引用来源。 |
| `error` | 错误信息。 |
| `done` | 流结束。 |
| `stop` | 预留，当前未使用。 |

## 3. 会话接口

### 3.1 创建会话

`POST /api/conversations`

#### Query 参数

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|---|---|---|---|---|
| `user_id` | `int` | 否 | `1` | 用户 ID。 |

#### 请求体

```json
{
  "title": "我的新对话"
}
```

请求体可省略。`title` 最长 200 字符，不传或空白时使用默认标题 `新对话`。

#### 响应示例

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "session_id": 7890123456789012345,
    "title": "我的新对话",
    "message_count": 0,
    "last_message_at": "2026-06-29T10:00:00",
    "created_at": "2026-06-29T10:00:00"
  }
}
```

#### curl

```bash
curl -X POST "http://127.0.0.1:8000/api/conversations?user_id=1" \
  -H "Content-Type: application/json" \
  -d '{"title":"测试会话"}'
```

### 3.2 查询会话列表

`GET /api/conversations`

#### Query 参数

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|---|---|---|---|---|
| `page` | `int` | 否 | `1` | 页码，必须大于等于 1。 |
| `size` | `int` | 否 | `20` | 每页条数，范围 1 到 100。 |
| `user_id` | `int` | 否 | `1` | 用户 ID。 |

#### 响应示例

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "items": [
      {
        "session_id": 7890123456789012345,
        "title": "技术监督是什么",
        "message_count": 4,
        "last_message_at": "2026-06-29T11:30:00",
        "created_at": "2026-06-29T10:00:00"
      }
    ],
    "total": 1,
    "page": 1,
    "size": 20
  }
}
```

### 3.3 修改会话标题

`PATCH /api/conversations/{conversation_id}`

#### 路径参数

| 参数 | 类型 | 说明 |
|---|---|---|
| `conversation_id` | `int` | 会话 ID。 |

#### 请求体

```json
{
  "title": "新的会话标题"
}
```

`title` 必填，长度范围 1 到 200 字符。

#### 响应示例

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "session_id": 7890123456789012345,
    "title": "新的会话标题",
    "message_count": 4,
    "last_message_at": "2026-06-29T11:30:00",
    "created_at": "2026-06-29T10:00:00"
  }
}
```

### 3.4 删除会话

`DELETE /api/conversations/{conversation_id}`

#### 响应示例

```json
{
  "code": 0,
  "message": "success",
  "data": null
}
```

会话不存在时返回 `404`：

```json
{
  "detail": "会话不存在"
}
```

### 3.5 查询会话消息

`GET /api/conversations/{conversation_id}/messages`

#### Query 参数

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|---|---|---|---|---|
| `page` | `int` | 否 | `1` | 页码，必须大于等于 1。 |
| `size` | `int` | 否 | `50` | 每页条数，范围 1 到 200。 |

#### 响应示例

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "session_id": 7890123456789012345,
    "title": "技术监督是什么",
    "total": 2,
    "messages": [
      {
        "message_id": 7890123456789012346,
        "seq": 1,
        "role": "user",
        "content": "什么是技术监督？",
        "intent_type": null,
        "thinking_steps": null,
        "citations": null,
        "generate_status": 1,
        "token_usage": null,
        "created_at": "2026-06-29T10:01:00",
        "updated_at": "2026-06-29T10:01:00"
      },
      {
        "message_id": 7890123456789012347,
        "seq": 2,
        "role": "assistant",
        "content": "技术监督是指...",
        "intent_type": "KNOWLEDGE_QA",
        "thinking_steps": "[{\"type\":\"intent\",\"message\":\"识别意图: KNOWLEDGE_QA\",\"timestamp\":1782734400.0,\"elapsed_ms\":null}]",
        "citations": "[{\"index\":1,\"doc_id\":\"doc_001\",\"doc_name\":\"技术监督管理办法.pdf\",\"snippet\":\"技术监督是指...\"}]",
        "generate_status": 1,
        "token_usage": null,
        "created_at": "2026-06-29T10:01:05",
        "updated_at": "2026-06-29T10:01:05"
      }
    ]
  }
}
```

`thinking_steps` 和 `citations` 以 JSON 字符串形式存储，前端读取历史消息时需要自行 `JSON.parse`。旧数据可能仍包含历史 `event_type` 字段，新生成数据使用 `type` 字段。

## 4. Agent 测试接口

### 4.1 非持久化测试调用

`POST /api/chat/test`

该接口直接调用 `agent_graph.ainvoke()`，不创建会话、不写数据库，适合本地调试、接口联调和无 MySQL 环境下验证 Agent 行为。

#### 请求体

```json
{
  "question": "什么是技术监督？",
  "selected_kb_ids": [1, 2],
  "user_id": 7,
  "messages": [
    {
      "role": "assistant",
      "content": "你好，请问有什么可以帮您？"
    }
  ]
}
```

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `question` | `string` | 是 | 当前用户问题，不能为空。 |
| `selected_kb_ids` | `int[]` | 否 | 检索限定的知识库 ID。 |
| `user_id` | `int | null` | 否 | 用户 ID。 |
| `messages` | `array` | 否 | 历史消息，`role` 只允许 `system`、`user`、`assistant`。 |

如果 `messages` 最后一条不是当前 `question` 对应的用户消息，服务端会自动追加一条 `{"role":"user","content":question}`。

#### 响应示例

```json
{
  "intent": "KNOWLEDGE_QA",
  "mode": "rag",
  "needs_clarification": false,
  "classification_source": "rule",
  "retrieved_docs_count": 2,
  "thinking_steps": [
    {
      "type": "intent",
      "message": "识别意图: KNOWLEDGE_QA",
      "timestamp": 1782734400.0,
      "elapsed_ms": null
    }
  ],
  "citations": [
    {
      "index": 1,
      "doc_id": "doc_001",
      "doc_name": "技术监督管理办法.pdf",
      "snippet": "技术监督是指...",
      "full_snippet": "技术监督是指..."
    }
  ],
  "final_response": "技术监督是指..."
}
```

#### curl

```bash
curl -X POST "http://127.0.0.1:8000/api/chat/test" \
  -H "Content-Type: application/json" \
  -d '{"question":"什么是技术监督？","selected_kb_ids":[1]}'
```

## 5. SSE 流式聊天接口

### 5.1 发送消息

`POST /api/chat`

该接口会校验会话、保存用户消息、加载历史消息、调用 `agent_graph.astream(..., stream_mode=["updates", "custom"])`，并通过 SSE 返回思考过程、token 级回答增量、引用来源和结束事件。

#### 请求头

```text
Content-Type: application/json
Accept: text/event-stream
```

#### 请求体

```json
{
  "conversation_id": 7890123456789012345,
  "question": "什么是技术监督？",
  "user_id": 1,
  "selected_kb_ids": [1, 2]
}
```

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `conversation_id` | `int` | 是 | 会话 ID，必须存在且未删除。 |
| `question` | `string` | 是 | 当前用户问题，不能为空。 |
| `user_id` | `int | null` | 否 | 用户 ID，不传时使用默认值 `1`。 |
| `selected_kb_ids` | `int[]` | 否 | 检索限定的知识库 ID。 |

### 5.2 SSE 基本格式

每个事件由 `event` 和 `data` 组成，`data` 是 JSON 字符串：

```text
event: thinking
data: {"type":"thinking","step_type":"intent","message":"识别意图: KNOWLEDGE_QA","elapsed_ms":null}
```

### 5.3 thinking 事件

内部 `thinking_steps` 使用 `type` 字段，SSE 输出时转换为 `step_type`：

```json
{
  "type": "thinking",
  "step_type": "retrieve",
  "message": "检索到 5 条片段",
  "elapsed_ms": 320
}
```

可能的 `step_type` 包括 `intent`、`clarify`、`retrieve`、`rerank`、`generate`、`citation`。

### 5.4 message 事件

流式增量事件：

```json
{
  "delta": "技",
  "content": "技",
  "message_id": 7890123456789012347,
  "finished": false
}
```

最终确认事件：

```json
{
  "delta": "",
  "content": "技术监督是指...",
  "message_id": 7890123456789012347,
  "intent": "KNOWLEDGE_QA",
  "finished": true
}
```

如果底层模型没有输出 token 增量，但最终状态有回答，服务端会补发一次 `finished=false` 的完整内容事件，然后再发送 `finished=true` 的最终确认事件。

### 5.5 citation 事件

同一次回答最多发送一个 `citation` 事件。引用在生成节点中由 `build_citations()` 构建，并通过 `merge_consecutive_citations()` 合并同文档连续引用。

```json
{
  "type": "citation",
  "citations": [
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
  ],
  "merged": true
}
```

### 5.6 error 事件

```json
{
  "message": "会话不存在"
}
```

已知错误场景：

| 场景 | 行为 |
|---|---|
| `question` 为空 | HTTP `400`，不进入 SSE。 |
| 数据库会话初始化失败 | SSE `error` 后跟 `done`。 |
| 会话不存在 | SSE `error` 后跟 `done`。 |
| Agent 运行失败 | 更新 assistant 消息为失败状态，SSE `error` 后跟 `done`。 |

### 5.7 done 事件

正常完成：

```json
{
  "message_id": 7890123456789012347,
  "conversation_id": 7890123456789012345
}
```

错误结束时可能是空对象 `{}`。

### 5.8 完整 SSE 示例

```text
event: thinking
data: {"type":"thinking","step_type":"intent","message":"识别意图: KNOWLEDGE_QA","elapsed_ms":null}

event: thinking
data: {"type":"thinking","step_type":"retrieve","message":"检索到 2 条相关片段","elapsed_ms":null}

event: message
data: {"delta":"技","content":"技","message_id":7890123456789012347,"finished":false}

event: message
data: {"delta":"术监督","content":"技术监督","message_id":7890123456789012347,"finished":false}

event: thinking
data: {"type":"thinking","step_type":"generate","message":"回答生成完成","elapsed_ms":null}

event: citation
data: {"type":"citation","citations":[{"doc_id":"doc_001","indices":[1,2],"doc_name":"技术监督管理办法.pdf"}],"merged":true}

event: message
data: {"delta":"","content":"技术监督是指...","message_id":7890123456789012347,"intent":"KNOWLEDGE_QA","finished":true}

event: done
data: {"message_id":7890123456789012347,"conversation_id":7890123456789012345}
```

#### curl

```bash
curl -N -X POST "http://127.0.0.1:8000/api/chat" \
  -H "Content-Type: application/json" \
  -H "Accept: text/event-stream" \
  -d '{
    "conversation_id": 7890123456789012345,
    "question": "什么是技术监督？",
    "selected_kb_ids": [1]
  }'
```

## 6. 前端接入示例

```javascript
const response = await fetch("/api/chat", {
  method: "POST",
  headers: { "Content-Type": "application/json" },
  body: JSON.stringify({
    conversation_id: sessionId,
    question: "什么是技术监督？",
    selected_kb_ids: [1],
  }),
});

const reader = response.body.getReader();
const decoder = new TextDecoder();
let buffer = "";

while (true) {
  const { done, value } = await reader.read();
  if (done) break;

  buffer += decoder.decode(value, { stream: true });
  const blocks = buffer.split("\n\n");
  buffer = blocks.pop() || "";

  for (const block of blocks) {
    const eventLine = block.match(/^event: (.+)$/m);
    const dataLine = block.match(/^data: (.+)$/m);
    if (!eventLine || !dataLine) continue;

    const event = eventLine[1];
    const data = JSON.parse(dataLine[1]);

    if (event === "thinking") {
      renderThinking(data.step_type, data.message, data.elapsed_ms);
    }
    if (event === "message") {
      renderAnswer(data.content, data.finished);
    }
    if (event === "citation") {
      renderCitations(data.citations);
    }
    if (event === "error") {
      renderError(data.message);
    }
  }
}
```

## 7. 典型调用链路

```text
1. POST /api/conversations
2. POST /api/chat
3. GET /api/conversations/{conversation_id}/messages
4. GET /api/conversations
5. PATCH /api/conversations/{conversation_id}
6. DELETE /api/conversations/{conversation_id}
```

## 8. 本地运行

```bash
cd qa-agent
pip install -r requirements.txt
copy .env.example .env
alembic upgrade head
uvicorn main:app --reload --host 0.0.0.0 --port 8000
```

SSE 接口建议使用 `curl -N`、前端 fetch stream 或自动化测试验证，不建议只依赖 Swagger。

## 9. 验证命令

在项目根目录运行：

```bash
python -m pytest qa-agent/tests/test_chat.py -v
python -m compileall qa-agent qa_agent
```

当前已验证结果：`32 passed`，编译检查通过。

## 10. 实现文件

| 接口/能力 | 文件 |
|---|---|
| `/api/chat`、`/api/chat/test` | `qa-agent/api/chat.py` |
| 会话 CRUD 与消息查询 | `qa-agent/api/conversation.py` |
| 请求/响应模型 | `qa-agent/api/schemas.py` |
| 数据库会话 | `qa-agent/db/session.py` |
| 数据库 CRUD | `qa-agent/db/repository.py` |
| Agent 工作流 | `qa-agent/graph/workflow.py` |
| Agent 节点 | `qa-agent/graph/nodes.py` |
| 上下文构建 | `qa-agent/graph/context.py` |
| 思考步骤转换 | `qa-agent/service/thinking_service.py` |
| 引用构建与 SSE 转换 | `qa-agent/service/citation_service.py` |
