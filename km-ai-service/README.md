# km-ai-service

知识管理组 **Python 并列服务**：chunk 切分、embedding、rerank、Milvus 读写。

- 内部 API：`docs/ai-service-contract.yaml`
- 大组方案：`docs/知识管理组-中间件与服务方案.md`
- 默认端口：**8092**

## 职责

| 模块 | 说明 |
|------|------|
| `chunker/` | 按知识库策略切分文档 |
| `embedder/` | 文本向量化 |
| `reranker/` | 检索结果重排 |
| `milvus/` | 向量存储与检索 |
| `consumer/` | 消费 RabbitMQ 文档任务 |

## 本地启动（骨架待 EPIC-04 实现）

```bash
cd km-ai-service
pip install -r requirements.txt
uvicorn app.main:app --host 0.0.0.0 --port 8092 --reload
```

依赖中间件：`docker compose up -d`（含 Milvus、RabbitMQ、MinIO）。

实现进度见分支 `feat/a-pipeline-v2`（EPIC-04，合入 `feat-a` 后本目录补充完整代码）。
