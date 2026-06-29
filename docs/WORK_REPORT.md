# qa-agent A/B/C 合并工作报告

日期：2026-06-29

## 1. 工作目标

本次工作目标是将 A、B、C 三部分用户手册中描述的智能问答能力合并为一个一致的 `qa-agent` 模块，重点解决工作流、HTTP/SSE 接口、上下文、思考过程和引用溯源之间的运行时合同不一致问题。

## 2. 完成内容

- 合并 A 侧 LangGraph 编排、B 侧 FastAPI/SSE 与会话持久化、C 侧上下文/思考过程/引用格式化能力。
- 将工作流节点的 `thinking_steps` 统一为 `type`、`message`、`timestamp`、`elapsed_ms` 结构。
- 在生成节点接入多轮上下文构建，避免重复当前问题，同时保留知识库片段优先级。
- 将引用构建统一到 `build_citations` 和 `merge_consecutive_citations`，支持长片段截断、完整片段保留和同文档连续引用合并。
- 将 `/api/chat` 的 `thinking` 和 `citation` SSE 事件改为统一前端合同，并保留 token 级 `message` 增量流式输出。
- 新增 `/api/chat/test` 调试接口，支持不依赖数据库的 Agent 图调用测试。
- 增加集成契约测试，覆盖上下文、思考步骤、引用合并、SSE 转换、数据库初始化失败降级和测试接口调用。

## 3. 关键文件

| 文件 | 说明 |
|---|---|
| `qa-agent/graph/nodes.py` | 接入多轮上下文、规范思考步骤和规范引用构建。 |
| `qa-agent/api/chat.py` | 提供 `/api/chat` SSE 和 `/api/chat/test` 测试接口。 |
| `qa-agent/api/schemas.py` | 定义聊天请求、历史消息和测试接口响应模型。 |
| `qa-agent/db/session.py` | 延迟初始化数据库引擎，便于缺少数据库驱动时返回受控 SSE 错误。 |
| `qa-agent/tests/test_chat.py` | 增加 A/B/C 跨层契约测试。 |
| `.trellis/spec/backend/quality-guidelines.md` | 沉淀 `qa-agent` HTTP/SSE 合同验证要求。 |

## 4. 接口验证结果

已通过 FastAPI 应用层接口自测，覆盖以下 7 个接口：

| 接口 | 结果 |
|---|---|
| `GET /api/conversations` | 通过 |
| `POST /api/conversations` | 通过 |
| `PATCH /api/conversations/{conversation_id}` | 通过 |
| `GET /api/conversations/{conversation_id}/messages` | 通过 |
| `DELETE /api/conversations/{conversation_id}` | 通过 |
| `POST /api/chat/test` | 通过 |
| `POST /api/chat` | 通过，SSE 事件包含 `thinking`、`message`、`citation`、`done` |

## 5. 自动化验证

| 命令 | 结果 |
|---|---|
| `python -m pytest qa-agent/tests/test_chat.py -v` | `32 passed` |
| `python -m compileall qa-agent qa_agent` | 通过 |

验证中未连接真实 DeepSeek、MySQL 或知识库服务，测试通过 monkeypatch/fake 边界对象验证接口和跨层合同。

## 6. 当前运行时合同

### 6.1 Thinking Step 内部结构

```json
{
  "type": "retrieve",
  "message": "检索到 5 条片段",
  "timestamp": 1782734400.0,
  "elapsed_ms": 320
}
```

### 6.2 Thinking SSE 结构

```json
{
  "type": "thinking",
  "step_type": "retrieve",
  "message": "检索到 5 条片段",
  "elapsed_ms": 320
}
```

### 6.3 Citation SSE 结构

```json
{
  "type": "citation",
  "citations": [
    {
      "doc_id": "doc_001",
      "doc_name": "技术监督管理办法.pdf",
      "indices": [1, 2],
      "snippet": "技术监督是指...",
      "full_snippet": "技术监督是指..."
    }
  ],
  "merged": true
}
```

### 6.4 Message SSE 结构

流式增量：

```json
{
  "delta": "技",
  "content": "技",
  "message_id": 7890123456789012347,
  "finished": false
}
```

最终确认：

```json
{
  "delta": "",
  "content": "技术监督是指...",
  "message_id": 7890123456789012347,
  "intent": "KNOWLEDGE_QA",
  "finished": true
}
```

## 7. 未完成与边界

- JWT 鉴权未接入，当前 `user_id` 仍由请求参数或请求体传入。
- 真实知识库检索、文档入库、知识库 CRUD 不在本次合并范围内。
- 报告生成、知识库管理、任务动作类意图当前只识别并进入澄清，不执行实际动作。
- 旧历史消息中可能仍存在 `event_type` 形态的 `thinking_steps` JSON，本次不做数据库迁移。

## 8. 后续建议

- 前端接入时以 `qa-agent/API.md` 中的 SSE 合同为准。
- 知识库服务接入后补充真实检索端到端测试。
- 如需兼容历史消息展示，可在前端或后端读取历史消息时增加旧 `event_type` 到新 `type` 的只读转换。
