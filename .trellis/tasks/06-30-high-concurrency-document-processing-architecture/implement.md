# High Concurrency Document Processing Implementation Plan

## Review Gate

- Do not start implementation until `prd.md`, `design.md`, this plan, `implement.jsonl`, and `check.jsonl` are reviewed and the task is moved to `in_progress` with `task.py start`.
- Initial capacity target is approved as local MVP stability with scale-out hooks: default one Python worker to `1-2` concurrent MinerU jobs and RabbitMQ prefetch `1-2`.

## Ordered Checklist

1. Confirm runtime profile uses MinIO for shared worker access, or document why local storage is acceptable only for local MVP.
2. Add Java queue configuration for durable document-processing exchange, queue, routing key, retry, and DLQ.
3. Extend Java document upload flow so queue publishing occurs after document metadata and raw file storage succeed.
4. Add or finalize Java internal status update endpoint or Java result-message consumer.
5. Add Python `km-ai-service` RabbitMQ consumer skeleton with configurable prefetch and bounded worker concurrency.
6. Port reusable behavior from `km-ingest-service`: MinerU command execution, artifact collection, parsed artifact upload, and manifest normalization.
7. Add idempotent chunk/vector write behavior for `documentId` retries.
8. Add status transitions and normalized failure reporting from Python to Java.
9. Add observability: correlation IDs, job IDs, document IDs, attempt counts, and parse duration logs.
10. Add smoke validation path for upload, enqueue, worker processing, status transition, and failure handling.
11. Update docs to reflect `km-ingest-service` prototype status and `km-ai-service` production ingestion responsibility.

## Validation Commands

- Java compile: `mvn -pl km-backend -am compile`.
- Java tests: `mvn -pl km-backend -am test`.
- Middleware startup: `docker compose up -d rabbitmq minio redis milvus mysql`.
- Backend health: `curl http://localhost:8091/api/health`.
- Python worker health: `curl http://localhost:8092/internal/health`.
- Upload smoke: `curl -F "file=@sample.pdf" http://localhost:8091/api/knowledge-bases/{kbId}/documents`.
- Queue smoke: verify RabbitMQ queue receives and drains one document-processing message.
- Status smoke: poll `GET /api/documents/{id}` until `READY` or `FAILED`.

## Risky Files

- `km-backend/src/main/java/com/km/service/impl/DocumentServiceImpl.java`: upload transaction and queue publish timing.
- `km-backend/src/main/java/com/km/controller/document/DocumentController.java`: public API must remain stable.
- `km-backend/src/main/resources/application-dev.yml`: local storage currently defaults to `local`, which is not suitable for multi-worker Python reading unless file sharing is configured.
- `docs/ai-service-contract.yaml`: internal contract changes can affect Java/Python integration.
- `km-ingest-service/app/pipeline.py` and `km-ingest-service/app/mineru_runner.py`: prototype behavior to port without preserving unwanted service coupling.

## Rollback Points

- Keep queue publishing feature-gated until worker and status path pass smoke tests.
- Keep upload-to-metadata behavior independent from queue publishing failure policy; decide whether enqueue failure makes upload fail or leaves document in a retryable pending state.
- Keep prototype `km-ingest-service` untouched until `km-ai-service` reaches parity.

## Pre-Start Checks

- Capacity target selected and reflected in worker defaults.
- `implement.jsonl` and `check.jsonl` include real context entries.
- User reviews and approves the design boundary: Java public API/orchestrator, Python worker/parser, RabbitMQ async dispatch.

## Implementation Notes

- Java upload now publishes document-processing jobs after transaction commit; publish failures are logged and do not block upload.
- Java exposes `POST /internal/documents/status` for worker status callbacks.
- Python `km-ai-service` now has a FastAPI health endpoint and RabbitMQ worker skeleton with conservative prefetch/concurrency defaults.
- MinerU artifact normalization now skips image file collection/upload and strips Markdown image references; only `content.md`, `middle.json`, and `layout.json` are uploaded for RAG/status.
- New MinIO object keys are human-readable and safe: raw uploads use `raw/kb-{kbName}--{shortKbId}/{filename}--doc-{documentId}.{ext}`, parsed artifacts use `parsed/kb-{kbName}--{shortKbId}/{filename}--doc-{documentId}/...`.
- Validation completed: `python -m compileall km-ai-service\app` passes.
- Validation blocked: `mvn -pl km-backend -am compile` could not run because `mvn` is not available in the current shell and the repository has no Maven wrapper.

## Continuation Notes For Chunking And Provenance

### Current Processing Boundary

- Java `km-backend` remains the only public upload/status API. Upload creates the durable `document` row, stores the original file in MinIO, then publishes a RabbitMQ task after commit.
- Python `km-ai-service` consumes RabbitMQ, downloads the original file from MinIO, runs MinerU, normalizes parser outputs, uploads text/json artifacts, and calls Java status updates.
- `DocumentProcessJob` is a Python runtime object only. It is created from the RabbitMQ JSON message and is not persisted after the worker finishes.
- Durable identifiers live in MySQL and MinIO, not in `DocumentProcessJob`: `document.id`, `document.kb_id`, `document.filename`, `document.file_path`, and parsed object keys.

### Message And Runtime Context

- Java message DTO: `km-backend/src/main/java/com/km/dto/document/DocumentProcessMessage.java`.
- Python runtime DTO: `km-ai-service/app/processor.py` class `DocumentProcessJob`.
- Required context currently carried across Java -> RabbitMQ -> Python:
  - `documentId`: stable primary key for document/chunk/provenance.
  - `kbId`: stable primary key for knowledge-base filtering.
  - `kbName`: human-readable knowledge-base segment for MinIO browsing only.
  - `rawObject`: exact MinIO object key for the original uploaded file.
  - `filename`: original display filename and readable artifact path segment.
  - `chunkStrategy`: JSON string from `knowledge_base.chunk_strategy_json`.
  - `callbackUrl`: Java internal status callback.
- Do not reconstruct object keys later by guessing from names. Pass exact object keys forward or read them from DB/status metadata.

### MinIO Naming Contract

- Original files use `raw/kb-{kbName}--{shortKbId}/{filename}--doc-{documentId}.{ext}`.
- Parsed artifacts use `parsed/kb-{kbName}--{shortKbId}/{filename}--doc-{documentId}/...`.
- Uploaded parsed files are currently limited to:
  - `content.md`: normalized Markdown used for text RAG/chunking.
  - `middle.json`: MinerU structured intermediate output for layout/debug/future provenance.
  - `layout.json`: MinerU layout summary when present.
- Image artifacts are intentionally not uploaded. `MINERU_ENABLE_IMAGE_ANALYSIS=false` disables MinerU image-analysis mode, and `artifacts.py` strips Markdown image embeds from `content.md`.
- The readable MinIO prefix is for humans in MinIO Console. Program logic should treat the exact object key as data, not as a naming convention to parse.

### `middle.json` Meaning

- `middle.json` is MinerU's structured intermediate parse output, not an image artifact.
- It commonly contains page-level data such as layout blocks, text lines/spans, bounding boxes, block types, OCR/layout scores, and page metadata.
- MVP text RAG can chunk from `content.md` first. Keep `middle.json` for debugging, page-aware chunking, future source highlighting, and recovering page/section structure if needed.
- Do not use `middle.json` as the primary source unless the next task explicitly needs layout-aware chunking or page/bbox provenance.

### Recommended Chunk Pipeline Next

- Preferred MVP path inside the same Python worker:
  1. After MinerU completes, read local `normalized/content.md` directly instead of downloading it back from MinIO.
  2. Split text according to `DocumentProcessJob.chunk_strategy`.
  3. For every chunk, keep `documentId`, `kbId`, `filename`, `chunkIndex`, optional `pageNo`, optional `chapterPath`, and content.
  4. Write chunk rows with `chunk.doc_id = documentId`.
  5. If writing Milvus vectors, include metadata `documentId`, `kbId`, `filename`, `chunkIndex`, optional `pageNo`, optional `chapterPath`.
  6. Mark Java status `CHUNKING` before split, `EMBEDDING` before embedding, and `READY` only after chunk/vector writes finish.
- If chunking is split into a second worker, publish a follow-up message that includes exact fields: `documentId`, `kbId`, `filename`, `markdownObject`, `chunkStrategy`, and `callbackUrl`.
- Do not make the chunk worker scan MinIO and infer document identity from object names. Use message payloads or a persisted artifact-reference table/field.

### Provenance And Search Contract

- `chunk.doc_id` is the authoritative link from chunk back to document.
- The document display name should come from `document.filename`, not from MinIO path parsing.
- The original file for source opening should come from `document.file_path` and the existing Java download endpoint, not direct MinIO access from clients.
- Retrieval result assembly should follow this chain: vector/keyword hit -> `chunk.doc_id` -> `document.id` -> `document.filename`, `document.file_path`, `document.kb_id` -> optional `knowledge_base.name`.
- For source links in answers, return at least `documentId`, `filename`, `chunkIndex`, and content snippet. Add `pageNo` later if extracted from `middle.json` or Markdown page markers.
- If document rename or knowledge-base rename is added later, do not rename old MinIO objects automatically unless a migration is explicitly required; DB paths remain the source of truth.

### Validation To Run Tomorrow

- Restart Java after DTO/key changes so RabbitMQ messages include `kbName`.
- Restart Python worker after `.env` changes so `MINERU_ENABLE_IMAGE_ANALYSIS=false` is active.
- Upload one PDF and verify MinIO contains:
  - `raw/kb-.../{filename}--doc-{documentId}.pdf`.
  - `parsed/kb-.../{filename}--doc-{documentId}/content.md`.
  - `parsed/kb-.../{filename}--doc-{documentId}/middle.json` when MinerU emits it.
  - No uploaded image directories or `.png`/`.jpg` parser artifacts.
- Verify RabbitMQ message consumption reaches `READY` or a clear `FAILED` with error text.
- When Maven is available, run `mvn -pl km-backend -am compile` and `mvn -pl km-backend -am test`.
- Python syntax validation remains `python -m compileall km-ai-service\app`.
