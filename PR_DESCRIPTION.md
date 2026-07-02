# PR Description

## Summary

本次 PR 新增基于 MinerU 的文档解析链路，并将文档上传后的处理流程改造为 RabbitMQ 异步队列模式。同时调整文档 CRUD 相关接口与存储实现，接入 MinIO 作为原始文件和解析产物存储，并修复后端与 Python 解析服务之间的依赖和配置冲突问题。

## Changes

- 新增 `km-ai-service` Python 服务，提供文档处理 worker、MinerU 调用、解析产物归一化、MinIO 上传和后端状态回调能力。
- 上传文档后由 `km-backend` 持久化文档元数据并发布 RabbitMQ 处理消息，避免同步解析阻塞接口请求。
- 新增 RabbitMQ exchange、queue、DLX/DLQ 配置，支持文档解析失败后进入死信队列。
- 接入 MinIO 文件存储，原始文件写入 `raw/...` 路径，解析后的 `content.md`、`middle.json`、`layout.json` 写入 `parsed/...` 路径。
- 调整文档 CRUD 接口实现，补充用户 ID 解析、上传校验、下载、重试处理、标签更新、批量删除和状态回写逻辑。
- 新增内部状态回调接口，用于 Python worker 回写文档处理状态。
- 调整知识库和搜索相关接口的数据兼容逻辑，增强分页、状态筛选、删除校验和异常处理。
- 修复依赖冲突问题，更新后端 `pom.xml` 和 Python `requirements.txt`，并清理旧 `km-ingest-service` 实现。
- 将敏感连接信息改为环境变量占位，补充 `.env.example`、README 和本地运行说明。

## Processing Flow

1. 用户通过文档上传接口上传文件到指定知识库。
2. 后端校验文件类型和大小，将原始文件写入 MinIO，并创建文档记录。
3. 后端在事务提交后向 RabbitMQ 发布文档处理消息。
4. `km-ai-service` worker 消费消息，下载原始文件并调用 MinerU 解析。
5. worker 上传解析产物到 MinIO，并通过内部接口回写 `PARSING`、`READY` 或 `FAILED` 状态。
6. 处理失败的消息会 `nack` 且不重新入队，进入 DLQ 便于后续排查。

## Configuration

- 新增 RabbitMQ 配置：`DOCUMENT_PROCESSING_EXCHANGE`、`DOCUMENT_PROCESSING_QUEUE`、`DOCUMENT_PROCESSING_ROUTING_KEY`、`DOCUMENT_PROCESSING_DLX`、`DOCUMENT_PROCESSING_DLQ`。
- 新增 worker 并发配置：`RABBITMQ_PREFETCH_COUNT`、`MAX_CONCURRENT_PARSE_JOBS`。
- 新增 MinerU 配置：`MINERU_BACKEND`、`MINERU_TIMEOUT_SECONDS`、`MINERU_ENABLE_FORMULA`、`MINERU_ENABLE_TABLE`、`MINERU_ENABLE_IMAGE_ANALYSIS`。
- 新增 MinIO 配置：`MINIO_ENDPOINT`、`MINIO_ACCESS_KEY`、`MINIO_SECRET_KEY`、`MINIO_BUCKET`、`MINIO_SECURE`。
- 后端 `application.yml` 改为通过环境变量读取数据库、RabbitMQ、MinIO 和本地存储路径配置。

## Validation

- [ ] 启动 MySQL、Redis、RabbitMQ、MinIO。
- [ ] 启动 `km-backend`，确认 RabbitMQ 队列和 DLQ 自动声明成功。
- [ ] 启动 `km-ai-service` API：`uvicorn app.main:app --host 0.0.0.0 --port 8092 --reload`。
- [ ] 启动文档处理 worker：`python -m app.worker`。
- [ ] 上传 PDF 文档，确认接口快速返回且文档状态进入 `UPLOADED` 或 `PARSING`。
- [ ] 等待 worker 完成，确认文档状态更新为 `READY`，MinIO 中存在原始文件和解析产物。
- [ ] 使用 `KM_AI_SKIP_MINERU=true` 做 smoke test，验证队列消费和后端状态回调链路。
- [ ] 触发解析失败场景，确认文档状态为 `FAILED` 且消息进入 DLQ。

## Reviewer Notes

- 当前 MVP 默认保守并发：`MAX_CONCURRENT_PARSE_JOBS=1`、`RABBITMQ_PREFETCH_COUNT=1`，后续可通过增加 worker 实例扩展吞吐。
- 当前 worker 只上传 RAG/status 需要的 Markdown 和 JSON 产物，不上传 MinerU 图片目录，以降低本地 MVP 的存储和内存压力。
- `application.yml` 中的敏感配置已改为环境变量占位，部署时需要由环境变量或配置中心注入真实值。
