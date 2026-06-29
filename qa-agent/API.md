# 人员 B 接口文档

> 模块：对话管理 + SSE 流式聊天  
> 服务：`qa-agent`  
> 基础路径：`/api`  
> 默认端口：`8000`

---

## 1. 通用说明

### 1.1 服务地址

```
http://{host}:8000/api
```

本地示例：

```
http://127.0.0.1:8000/api
```

### 1.2 统一响应格式（REST 接口）

除 SSE 聊天接口外，其余接口均返回 JSON，结构如下：

```json
{
  "code": 0,
  "message": "success",
  "data": {}
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| code | int | 业务状态码，`0` 表示成功 |
| message | string | 提示信息 |
| data | object \| array \| null | 业务数据 |

### 1.3 HTTP 错误

| 状态码 | 场景 |
|--------|------|
| 400 | 请求参数不合法（如问题为空） |
| 404 | 会话不存在 |
| 422 | 请求体校验失败 |

FastAPI 校验失败示例：

```json
{
  "detail": [
    {
      "loc": ["body", "question"],
      "msg": "String should have at least 1 character",
      "type": "string_too_short"
    }
  ]
}
```

### 1.4 鉴权说明

当前开发阶段**未接入 JWT**，`user_id` 通过 Query 参数传入，默认值为 `1`。

后续接入鉴权后，`user_id` 将从 Token 解析，Query 参数会逐步废弃。

### 1.5 数据表

| 表名 | 说明 |
|------|------|
| qa_session | 会话表 |
| qa_message | 消息表 |

主键均为雪花 ID（`BigInteger`）。删除操作为**软删除**（`status=0`）。

---

## 2. 枚举与常量

### 2.1 消息角色 `role`

| 值 | 说明 |
|----|------|
| user | 用户消息 |
| assistant | AI 回复 |
| system | 系统消息 |

### 2.2 生成状态 `generate_status`

| 值 | 说明 |
|----|------|
| 0 | 生成中 |
| 1 | 已完成 |
| 2 | 失败 |

### 2.3 意图类型 `intent_type`（由 Agent 返回）

| 值 | 说明 |
|----|------|
| CHAT | 普通聊天 |
| KNOWLEDGE_QA | 知识库问答 |
| DOCUMENT_SEARCH | 文档检索 |

### 2.4 SSE 事件类型 `event`

| 值 | 说明 |
|----|------|
| thinking | 思考过程步骤 |
| message | 最终回答 |
| citation | 引用来源 |
| error | 错误信息 |
| done | 流结束 |
| stop | 预留，当前未使用 |

---

## 3. 对话管理接口

### 3.1 创建会话

**POST** `/api/conversations`

创建一个新的对话会话。

#### Query 参数

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| user_id | int | 否 | 1 | 用户 ID |

#### 请求体

```json
{
  "title": "我的新对话"
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| title | string | 否 | 会话标题，最长 200 字符；不传则默认为「新对话」 |

请求体可省略，省略时使用默认标题。

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

#### curl 示例

```bash
curl -X POST "http://127.0.0.1:8000/api/conversations?user_id=1" \
  -H "Content-Type: application/json" \
  -d '{"title": "测试会话"}'
```

---

### 3.2 会话列表

**GET** `/api/conversations`

分页查询当前用户的会话列表，按最后消息时间倒序。

#### Query 参数

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| page | int | 否 | 1 | 页码，≥ 1 |
| size | int | 否 | 20 | 每页条数，1～100 |
| user_id | int | 否 | 1 | 用户 ID |

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

#### curl 示例

```bash
curl "http://127.0.0.1:8000/api/conversations?page=1&size=20&user_id=1"
```

---

### 3.3 删除会话

**DELETE** `/api/conversations/{conversation_id}`

软删除指定会话及其全部消息。

#### 路径参数

| 参数 | 类型 | 说明 |
|------|------|------|
| conversation_id | int | 会话 ID |

#### 响应示例

```json
{
  "code": 0,
  "message": "success",
  "data": null
}
```

#### 错误响应

```json
{
  "detail": "会话不存在"
}
```

HTTP 状态码：`404`

#### curl 示例

```bash
curl -X DELETE "http://127.0.0.1:8000/api/conversations/7890123456789012345"
```

---

### 3.4 查询会话消息

**GET** `/api/conversations/{conversation_id}/messages`

分页查询指定会话的消息历史，按消息序号 `seq` 升序。

#### 路径参数

| 参数 | 类型 | 说明 |
|------|------|------|
| conversation_id | int | 会话 ID |

#### Query 参数

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| page | int | 否 | 1 | 页码，≥ 1 |
| size | int | 否 | 50 | 每页条数，1～200 |

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
        "thinking_steps": "[{\"event_type\":\"intent\",\"message\":\"识别意图: KNOWLEDGE_QA\"}]",
        "citations": "[{\"index\":1,\"doc_id\":\"doc_001\",\"doc_name\":\"技术监督管理办法.pdf\"}]",
        "generate_status": 1,
        "token_usage": null,
        "created_at": "2026-06-29T10:01:05",
        "updated_at": "2026-06-29T10:01:05"
      }
    ]
  }
}
```

> 说明：`thinking_steps`、`citations` 在数据库中以 JSON 字符串存储，前端需自行 `JSON.parse`。

#### curl 示例

```bash
curl "http://127.0.0.1:8000/api/conversations/7890123456789012345/messages?page=1&size=50"
```

---

### 3.5 修改会话标题

**PATCH** `/api/conversations/{conversation_id}`

修改指定会话的标题。

#### 路径参数

| 参数 | 类型 | 说明 |
|------|------|------|
| conversation_id | int | 会话 ID |

#### 请求体

```json
{
  "title": "新的会话标题"
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| title | string | 是 | 新标题，1～200 字符 |

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

#### curl 示例

```bash
curl -X PATCH "http://127.0.0.1:8000/api/conversations/7890123456789012345" \
  -H "Content-Type: application/json" \
  -d '{"title": "新的会话标题"}'
```

---

## 4. SSE 流式聊天接口

### 4.1 发送消息

**POST** `/api/chat`

向指定会话发送用户问题，通过 **Server-Sent Events (SSE)** 流式返回 Agent 处理过程与最终回答。

#### 请求头

```
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
|------|------|------|------|
| conversation_id | int | 是 | 会话 ID |
| question | string | 是 | 用户问题，不能为空 |
| user_id | int | 否 | 用户 ID，默认 1 |
| selected_kb_ids | int[] | 否 | 选中的知识库 ID 列表，传给 Agent 检索 |

#### 处理流程

```
1. 校验会话是否存在
2. 保存 user 消息到 qa_message
3. 加载历史消息，调用 agent_graph.astream()
4. 按节点进度推送 thinking 事件
5. 推送 citation 事件（如有检索结果）
6. 推送 message 事件（最终回答）
7. 更新 assistant 消息落库
8. 推送 done 事件
```

#### SSE 响应格式

每条 SSE 消息包含：

| 字段 | 说明 |
|------|------|
| event | 事件类型，见 §2.4 |
| data | JSON 字符串（需前端 parse） |

#### 事件详解

##### thinking — 思考过程

```
event: thinking
data: {"event_type":"intent","message":"识别意图: KNOWLEDGE_QA"}
```

```
event: thinking
data: {"event_type":"retrieve","message":"检索到 3 条相关片段"}
```

```
event: thinking
data: {"event_type":"rerank","message":"重排序后保留 3 条片段"}
```

```
event: thinking
data: {"event_type":"generate","message":"回答生成完成"}
```

##### citation — 引用来源

```
event: citation
data: {"index":1,"doc_id":"doc_001","doc_name":"技术监督管理办法.pdf","snippet":"技术监督是指...","score":0.92}
```

##### message — 最终回答

```
event: message
data: {"content":"技术监督是指...","message_id":7890123456789012347,"intent":"KNOWLEDGE_QA"}
```

##### error — 错误

```
event: error
data: {"message":"会话不存在"}
```

##### done — 流结束

```
event: done
data: {"message_id":7890123456789012347,"conversation_id":7890123456789012345}
```

#### 完整 SSE 示例

```
event: thinking
data: {"event_type": "intent", "message": "识别意图: KNOWLEDGE_QA"}

event: thinking
data: {"event_type": "retrieve", "message": "检索到 0 条相关片段"}

event: thinking
data: {"event_type": "generate", "message": "回答生成完成"}

event: message
data: {"content": "未找到相关知识库信息。...", "message_id": 7890123456789012347, "intent": "KNOWLEDGE_QA"}

event: done
data: {"message_id": 7890123456789012347, "conversation_id": 7890123456789012345}
```

#### curl 示例

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

#### 前端接入示例（JavaScript）

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

    switch (event) {
      case "thinking":
        console.log("[思考]", data.message);
        break;
      case "citation":
        console.log("[引用]", data.doc_name);
        break;
      case "message":
        console.log("[回答]", data.content);
        break;
      case "error":
        console.error("[错误]", data.message);
        break;
      case "done":
        console.log("[完成]", data);
        break;
    }
  }
}
```

---

## 5. 典型调用链路

```
1. POST /api/conversations          → 创建会话，拿到 session_id
2. POST /api/chat                   → 发送问题，SSE 接收回答
3. GET  /api/conversations/{id}/messages → 刷新历史消息
4. GET  /api/conversations          → 侧边栏展示会话列表
5. PATCH /api/conversations/{id}    → 修改标题
6. DELETE /api/conversations/{id}   → 删除会话
```

---

## 6. 业务规则

1. **标题自动生成**：会话默认标题为「新对话」；用户发送第一条消息后，若标题仍为默认值，会自动截取问题前 50 字作为标题。
2. **消息序号**：同一会话内 `seq` 从 1 递增，由服务端维护。
3. **软删除**：删除会话会同时将关联消息标记为已删除，不会物理删除。
4. **Agent 调用**：聊天接口内部调用人员 A 提供的 `agent_graph`，不直接调用 LLM。
5. **流式粒度**：当前 `message` 事件为整段回答一次性返回；`thinking` 事件按 Agent 节点进度推送。

---

## 7. 本地调试

```bash
cd qa-agent
pip install -r requirements.txt
cp .env.example .env   # 配置 DATABASE_URL、LLM_API_KEY
alembic upgrade head
uvicorn main:app --reload --host 0.0.0.0 --port 8000
```

Swagger 文档：

```
http://127.0.0.1:8000/docs
```

> SSE 接口建议在 Swagger 外用 curl 或前端 EventSource/fetch 测试。

---

## 8. 负责人与文件

| 接口 | 实现文件 |
|------|----------|
| POST /api/chat | `api/chat.py` |
| GET/POST/DELETE/PATCH /api/conversations | `api/conversation.py` |
| 请求/响应模型 | `api/schemas.py` |
| 数据库 CRUD | `db/repository.py` |
| ORM 模型 | `db/models.py` |
| 数据库迁移 | `alembic/versions/001_init_qa_session_message.py` |

维护人：**人员 B**
