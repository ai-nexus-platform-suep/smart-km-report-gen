# Agent Workflow

```mermaid
flowchart TD
    Start([START]) --> Intent[intent_node<br/>识别用户意图]

    Intent -->|CHAT| Generate[generate_node<br/>调用 DeepSeek 直接回答]
    Intent -->|KNOWLEDGE_QA / DOCUMENT_SEARCH| Retrieve[retrieve_node<br/>调用知识库 HTTP 检索适配器]

    Retrieve -->|无检索结果| GenerateFallback[generate_node<br/>提示未找到知识库信息<br/>再调用 DeepSeek 降级回答]
    Retrieve -->|有检索结果| Rerank[rerank_node<br/>按 score 重排序并截取 Top K]

    Rerank --> GenerateRag[generate_node<br/>注入知识片段生成回答]

    Generate --> End([END])
    GenerateFallback --> End
    GenerateRag --> End
```

## State Fields

- `question`: 用户当前问题
- `intent`: `CHAT` / `KNOWLEDGE_QA` / `DOCUMENT_SEARCH`
- `mode`: `direct` / `rag`
- `selected_kb_ids`: 用户选择的知识库范围
- `retrieved_docs`: 标准化后的检索片段
- `thinking_steps`: 给 SSE 思考过程展示使用的步骤
- `citations`: 回答引用来源
- `final_response`: 最终回答
- `error`: 受控错误信息

## Knowledge Adapter Contract

未来知识库组只需要提供 `KNOWLEDGE_SEARCH_URL` 对应的 HTTP 接口。Agent 会发送 `query`、`selected_kb_ids`、`top_k`、`similarity_threshold`、`embedding`，并接收包含 `doc_id`、`doc_name`、`kb_id`、`snippet`、`score`、`metadata` 的文档列表。
