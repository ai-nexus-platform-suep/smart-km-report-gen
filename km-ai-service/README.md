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
| `app/processor.py` | 下载 MinIO 原始文件、调用托管 MinerU Agent 文件上传解析、上传 Markdown/元数据产物、回写 Java 状态 |

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

MinerU Agent 默认超时为 `MINERU_TIMEOUT_SECONDS=600`，轮询间隔为 `MINERU_POLL_INTERVAL_SECONDS=2`。如果 RabbitMQ 中消息长时间停在 `Unacked`，通常表示 worker 正在等待托管解析任务；超过该时间后 worker 会把文档回调为 `FAILED` 并将消息转入 DLQ。

Worker 使用托管 MinerU Agent 文件上传解析模式：先把 MinIO 原始对象下载到本地工作目录，提交 `MINERU_AGENT_API_BASE_URL=/parse/file` 获取 `task_id` 和上传地址，再用 PUT 上传本地文件，轮询任务完成后下载 Markdown。这个流程不要求 MinIO 对公网开放，适合本地开发和私有 MinIO 环境。

托管轻量 API 限制：单文件不超过 10 MB，PDF 不超过 20 页，仅支持 PDF、图片、DOCX、PPTX、XLSX 等文件，不支持网页 HTML 或加密 PDF；接口免 Token，但可能按 IP 返回 429 限流。`MINERU_METHOD=ocr` 会以 `is_ocr=true` 强制 OCR，`MINERU_ENABLE_TABLE` 和 `MINERU_ENABLE_FORMULA` 会透传给托管解析接口。

托管轻量 API 当前只返回 Markdown。Worker 上传 `content.md` 和项目自有 `metadata.json`，不会伪造 `middle.json` 或 `layout.json`；normalized Markdown 中的图片引用也会移除，减少 MinIO 存储和下游 RAG 噪声。

MinIO 对象路径使用可读前缀：Java 原始文件写入 `raw/kb-{kbName}--{shortKbId}/{filename}--doc-{documentId}.{ext}`，Python 解析产物写入 `parsed/kb-{kbName}--{shortKbId}/{filename}--doc-{documentId}/...`。知识库名和文件名会被清理为安全片段，`documentId` 保证同名文件不冲突并支持重试覆盖。

Smoke 调试可以设置 `KM_AI_SKIP_MINERU=true`，worker 会跳过 MinerU，验证队列消费和 Java 状态回调链路。

如果后续方案改用 URL 解析、云对象存储、企业账号、外网反向代理等需要公共端点或云/企业前置条件的能力，必须在进入实现前明确提示这些部署前提，避免把本地不可复现的约束隐藏到实现中。

## MinerU Agent 配置

健康检查会显示当前托管解析配置：

```bash
curl http://localhost:8092/internal/health
```

常用配置：

```bash
MINERU_AGENT_API_BASE_URL=https://mineru.net/api/v1/agent
MINERU_TIMEOUT_SECONDS=600
MINERU_POLL_INTERVAL_SECONDS=2
```

不要把 MinerU 返回的上传地址或临时 `markdown_url` 写入持久化元数据；Worker 只保存稳定的文档 ID、文件名和 MinIO 对象路径。
