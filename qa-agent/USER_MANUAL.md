# 智能问答 Agent 用户手册

## 1. 文档目的

本文档用于说明人员 A 已完成的智能体工作流内容，并帮助人员 B、人员 C 以及后续知识库模块同学理解如何接入 `qa-agent`。

当前模块只负责智能问答 Agent 编排，不负责知识库管理、文档入库、报告生成。

## 2. 人员 A 已完成内容

人员 A 已完成 `qa-agent` 中的 LangGraph 智能体工作流骨架，主要包括：

- 修复 Python 包导入问题，使 `qa_agent.*` 可以正常 import。
- 接入 DeepSeek Chat Completions 兼容接口。
- 实现 Agent 状态定义 `AgentState`。
- 实现可解释意图识别、澄清、知识库检索、重排序、回答生成节点。
- 实现 HTTP-first 知识库检索适配器，方便后续知识库模块无缝接入。
- 实现无知识库、无 embedding、无外部检索接口时的安全降级。
- 提供 Agent 工作流图：`qa-agent/AGENT_WORKFLOW.md`。

核心文件：

- `qa-agent/graph/state.py`：Agent 状态字段定义。
- `qa-agent/graph/nodes.py`：LangGraph 节点逻辑。
- `qa-agent/graph/workflow.py`：工作流组装和条件路由。
- `qa-agent/client/knowledge_client.py`：知识库 HTTP 检索适配器。
- `qa-agent/client/java_client.py`：Java 配置拉取客户端，当前支持本地配置降级。
- `qa-agent/model/embedding.py`：embedding 调用封装。
- `qa-agent/model/reranker.py`：轻量重排序逻辑。
- `qa-agent/core/config.py`：全局配置。

## 3. Agent 工作流

当前 Agent 使用 LangGraph `StateGraph` 实现，流程如下：

```text
START
  ↓
intent_node
  ├─ CHAT → generate_node → END
  ├─ 低置信度 / 上下文不足 / 未开放动作 → clarify_node → END
  └─ KNOWLEDGE_QA / DOCUMENT_SEARCH
        ↓
     retrieve_node
        ├─ 无检索结果 → generate_node → END
        └─ 有检索结果
              ↓
           rerank_node
              ↓
           generate_node
              ↓
             END
```

节点说明：

- `intent_node`：识别用户问题意图。
- `clarify_node`：在问题上下文不足、置信度低或命中未开放动作时，返回澄清追问或能力边界说明。
- `retrieve_node`：调用知识库 HTTP 检索适配器。
- `rerank_node`：按文档 `score` 降序重排，并截取 Top K。
- `generate_node`：组装 prompt，调用 DeepSeek 生成回答。

当前支持的意图：

- `CHAT`：闲聊或直接回答。
- `KNOWLEDGE_QA`：知识问答，进入 RAG。
- `DOCUMENT_SEARCH`：文档检索，进入 RAG。
- `REPORT_GENERATION`：报告生成请求，本迭代只识别并澄清，不执行生成。
- `KB_MANAGEMENT`：知识库管理请求，本迭代只识别并澄清，不执行 CRUD。
- `TASK_ACTION`：任务动作请求，本迭代只识别并澄清，不执行动作。

## 4. 配置说明

本地运行前，需要创建 `.env` 文件。推荐放在以下任一位置：

- 项目根目录 `.env`
- `qa-agent/.env`

最小 DeepSeek 配置：

```env
LLM_API_URL=https://api.deepseek.com
LLM_API_KEY=你的 DeepSeek API Key
LLM_MODEL_NAME=deepseek-chat
LLM_TIMEOUT=60
```

知识库模块完成后，再补充：

```env
KNOWLEDGE_SEARCH_URL=http://知识库服务地址/api/knowledge/search
DEFAULT_TOP_K=5
DEFAULT_SIMILARITY_THRESHOLD=0.7
DEFAULT_RERANK_THRESHOLD=0.0
RETRIEVAL_TIMEOUT=10
```

注意：DeepSeek 当前主要用于聊天生成，不作为 embedding 服务。embedding 可以由知识库模块自行处理，或后续单独配置其他 embedding 服务。

## 5. 给人员 B 的接入说明

人员 B 负责 `api/chat.py` 和 SSE 流式接口时，只需要调用 A 提供的 `agent_graph`，不要修改 `graph/` 内部文件。

导入方式：

```python
from qa_agent.graph.workflow import agent_graph
```

最小调用示例：

```python
result = await agent_graph.ainvoke({
    "messages": [{"role": "user", "content": question}],
    "question": question,
    "conversation_id": conversation_id,
    "user_id": user_id,
    "selected_kb_ids": selected_kb_ids,
})
```

常用返回字段：

- `intent`：识别出的意图。
- `intent_confidence`：意图识别置信度。
- `route_reason`：路由原因，适合给 B 端展示思考过程或调试误判。
- `classification_source`：分类来源，可能是 `rule`、`llm`、`fallback`。
- `needs_clarification`：是否需要先向用户追问。
- `retrieved_docs`：检索到的知识片段。
- `thinking_steps`：思考过程步骤。
- `citations`：引用来源。
- `final_response`：最终回答。
- `error`：受控错误信息。

后续如果要做 SSE，可以基于 LangGraph 的事件流或先使用 `ainvoke` 返回结果再封装事件。

## 6. 给人员 C 的接入说明

人员 C 负责上下文管理、思考过程、引用溯源。当前 A 的工作流已经预留以下字段：

- `thinking_steps`
- `citations`
- `retrieved_docs`
- `messages`

人员 C 后续可以在自己负责的文件中完善：

- `qa-agent/graph/context.py`
- `qa-agent/service/thinking_service.py`
- `qa-agent/service/citation_service.py`
- `qa-agent/tests/test_chat.py`

建议接入点：

- 在 `generate_node` 生成 prompt 前，调用 C 的上下文拼装函数。
- 在 `retrieve_node`、`rerank_node`、`generate_node` 中，将步骤转换为 C 定义的思考过程格式。
- 在 `generate_node` 结束后，将 `retrieved_docs` 转换为引用结构。

人员 C 不需要修改 `workflow.py` 的整体流转，除非团队决定新增节点。

## 7. 给知识库模块同学的接入说明

知识库模块只需要提供一个 HTTP 检索接口，并把地址配置到：

```env
KNOWLEDGE_SEARCH_URL=http://知识库服务地址/api/knowledge/search
```

Agent 会发送如下 JSON：

```json
{
  "query": "用户问题",
  "selected_kb_ids": [1, 2],
  "top_k": 5,
  "similarity_threshold": 0.7,
  "embedding": null
}
```

接口可以返回文档数组：

```json
[
  {
    "doc_id": "doc_001",
    "doc_name": "技术监督管理办法.pdf",
    "kb_id": 1,
    "snippet": "技术监督是指...",
    "score": 0.92,
    "metadata": {}
  }
]
```

也可以返回包装对象：

```json
{
  "documents": [
    {
      "doc_id": "doc_001",
      "doc_name": "技术监督管理办法.pdf",
      "kb_id": 1,
      "snippet": "技术监督是指...",
      "score": 0.92,
      "metadata": {}
    }
  ]
}
```

Agent 会自动兼容 `documents`、`data`、`items`、`results` 这几种常见字段。

## 8. 本地 Demo

配置好 DeepSeek Key 后，可以运行：

```bash
python -c "import asyncio; from qa_agent.graph.workflow import agent_graph; result = asyncio.run(agent_graph.ainvoke({'messages': [{'role': 'user', 'content': '你好，请用一句话介绍你自己。'}], 'question': '你好，请用一句话介绍你自己。'})); print(result['intent']); print(result['final_response'])"
```

如果知识库接口还没接入，RAG 类问题会返回空检索降级结果：

```text
未找到相关知识库信息。...
```

这是预期行为，不是错误。

## 9. 协作边界

人员 A 后续主要维护：

- `qa-agent/graph/state.py`
- `qa-agent/graph/nodes.py`
- `qa-agent/graph/workflow.py`
- `qa-agent/model/embedding.py`
- `qa-agent/model/reranker.py`
- `qa-agent/core/config.py`
- `qa-agent/client/java_client.py`
- `qa-agent/client/knowledge_client.py`

人员 B 不要修改上述 Agent 内部实现，只通过 `agent_graph` 调用。

人员 C 不要修改工作流主结构，优先在自己的 context、thinking、citation 文件中完善能力。

知识库模块不需要了解 LangGraph 内部，只要实现 HTTP 检索接口即可。
