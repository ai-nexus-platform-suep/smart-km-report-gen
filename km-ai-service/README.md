# km-ai-service

知识管理组 **Python 并列服务**：文档解析 worker、chunk 切分、embedding、rerank、Milvus 读写。

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
| `app/worker.py` | 消费 RabbitMQ 文档处理任务 |
| `app/processor.py` | 下载原始文件、运行 MinerU、上传文本/JSON解析产物、回写 Java 状态 |

## 本地启动

```bash
cd km-ai-service
cp .env.example .env
pip install -r requirements.txt
uvicorn app.main:app --host 0.0.0.0 --port 8092 --reload
```

`.env` 是本地私有配置，不提交；迁移环境时复制 `.env.example` 或通过部署平台注入同名环境变量。

依赖中间件：RabbitMQ、MinIO、Milvus、MySQL、Redis。

## 文档处理 Worker

Java `km-backend` 上传文档后会发布 RabbitMQ 消息到 `km.document.processing.parse`。启动 worker：

```bash
cd km-ai-service
python -m app.worker
```

VS Code / IDE 也要按模块启动：`module=app.worker`，工作目录设为 `km-ai-service`。不要直接运行 `app/worker.py`，否则 Python 相对导入会失败。

本地 MVP 默认保守并发：`MAX_CONCURRENT_PARSE_JOBS=1`、`RABBITMQ_PREFETCH_COUNT=1`。需要提高吞吐时优先增加 worker 实例或调高这两个配置。

MinerU 默认超时为 `MINERU_TIMEOUT_SECONDS=600`。如果 RabbitMQ 中消息长时间停在 `Unacked`，通常表示 worker 正在等待 MinerU 子进程；超过该时间后 worker 会把文档回调为 `FAILED` 并将消息转入 DLQ。

本地 MVP 默认关闭重型识别：`MINERU_ENABLE_FORMULA=false`、`MINERU_ENABLE_IMAGE_ANALYSIS=false`，避免普通 PDF 在 MFR/图片分析模型阶段占用过多内存。Worker 只上传 RAG/status 需要的 `content.md`、`middle.json`、`layout.json`，不会上传 MinerU 图片目录；normalized Markdown 中的图片引用也会移除，减少 MinIO 存储和内存压力。

MinIO 对象路径使用可读前缀：Java 原始文件写入 `raw/kb-{kbName}--{shortKbId}/{filename}--doc-{documentId}.{ext}`，Python 解析产物写入 `parsed/kb-{kbName}--{shortKbId}/{filename}--doc-{documentId}/...`。知识库名和文件名会被清理为安全片段，`documentId` 保证同名文件不冲突并支持重试覆盖。

Smoke 调试可以设置 `KM_AI_SKIP_MINERU=true`，worker 会跳过 MinerU，验证队列消费和 Java 状态回调链路。
