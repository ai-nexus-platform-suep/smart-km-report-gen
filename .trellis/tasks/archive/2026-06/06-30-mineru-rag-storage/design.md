# Design: MinerU RAG Storage Pipeline

## Architecture

The full target architecture coordinates four storage layers, but the next implementation increment is a standalone Python ingest/parser service:

0. `km-ingest-service` receives a local file path or future upload request, writes the raw file to MinIO, runs MinerU, and writes parser artifacts to MinIO.
1. Spring Boot upload/API integration can call this service in a later stage.
2. Later pipeline stages split chunks, persist DB metadata, and write vectors.

Full target architecture:

1. Upload API stores the raw object in `FileStorageService`.
2. Parser pipeline invokes MinerU and stores full artifacts in MinIO-compatible object storage.
3. Relational database stores document metadata, chunk content, and citation/source-trace metadata.
4. Vector service stores embeddings and minimal metadata for recall.

## Storage Boundaries

### MinIO/Object Storage

- Raw file: `raw/{docId}/source.{ext}`.
- Parsed markdown: `parsed/{docId}/content.md`.
- Parser JSON: `parsed/{docId}/middle.json`, `parsed/{docId}/layout.json`.
- Images: `parsed/{docId}/images/{imageName}` with stable names from MinerU or normalized page names.
- Object storage is the source of truth for full rendered artifacts, not for per-search chunk text.

### Standalone Python Service

- Directory: `km-ingest-service/` beside `km-backend/`, `km-common/`, `km-frontend/`, and `km-ai-service/`.
- Stage-1 responsibility: local file -> MinIO raw object -> MinerU output directory -> MinIO parsed objects -> manifest.
- Recommended initial interface: CLI command such as `python -m app ingest --file <path> --doc-id <optional>`; a FastAPI wrapper can be added later.
- Dependency style: project-local `.venv` and `requirements.txt` or `pyproject.toml`; keep the first version simple.
- MinIO client dependency: Python `minio` SDK.
- MinerU invocation for MVP: CLI subprocess using `mineru -p <input_path> -o <output_dir> -b pipeline` because it avoids building a MinerU HTTP service before basic artifact flow is proven.
- Temporary files and output should stay under `km-ingest-service/.work/` or a configured non-C path.
- Service should produce a manifest JSON containing `doc_id`, raw object path, markdown path, JSON artifact paths, image paths, parser backend, and errors when present.

### Non-C Install And Cache Strategy

- Virtual environment: `km-ingest-service/.venv`.
- Pip cache: `km-ingest-service/.cache/pip` via `PIP_CACHE_DIR`.
- Hugging Face cache: `km-ingest-service/.cache/huggingface` via `HF_HOME` / `HUGGINGFACE_HUB_CACHE`.
- ModelScope cache: `km-ingest-service/.cache/modelscope` via `MODELSCOPE_CACHE`.
- MinerU config/cache: `km-ingest-service/.mineru` and `km-ingest-service/.cache/mineru` where supported.
- Model download: run `mineru-models-download`, then move downloaded model directory to a non-C location and update `mineru.json`, if MinerU writes to the user directory by default.
- If a dependency ignores cache settings and writes to `C:\Users\...`, document the remaining path and move/configure it before continuing.

### Relational Database

- `document` should gain parser artifact path fields or a structured artifact JSON field. Minimal recommended fields:
  - `raw_file_path` or reuse/rename semantics of `file_path` for raw source path.
  - `source_md_path` for `parsed/{docId}/content.md`.
  - `parser_artifacts_json` for `middle.json`, `layout.json`, image prefix/list, and parser backend/version.
- `chunk` should gain:
  - `source_md_path VARCHAR(512)`
  - `page_no INT`
  - `block_ids JSON`
  - `bbox JSON`
- Database chunk text is the authoritative source for citation hover, fallback keyword search, and business audit.

### Vector Store / AI Service

- Recommended MVP vector database: Qdrant.
- Recommended runtime: Qdrant via Docker Compose for local development and demo deployment.
- Qdrant collection name should be environment-configurable, with one point per `chunk_id`.
- Qdrant point ID should be stable from `chunk_id` or a deterministic UUID derived from `chunk_id`.
- Store or upsert vectors keyed by `chunk_id`.
- Metadata should include `chunk_id`, `doc_id`, `page_no`, and optional preview text only.
- Do not rely on vector metadata for full chunk content, block mapping, or artifact paths.

### Why Qdrant

- It is practical for a small student team: one Docker service is enough for local and demo deployments.
- Docker is recommended, not mandatory; alternatives include Qdrant Cloud or a native/server binary, but those add account or environment differences.
- It exposes straightforward HTTP/gRPC APIs, so it can sit behind the existing backend/AI-service boundary.
- Payload filtering supports common RAG filters such as `kb_id`, `doc_id`, and `page_no` without putting business data into the vector store.
- It has lower operational complexity than Milvus for an MVP.
- It avoids a database switch from the current MySQL stack to PostgreSQL just to use pgvector.

### Alternatives Considered

- Milvus: powerful and scalable, but heavier operationally for a graduation-project team.
- pgvector: very simple if the project already uses PostgreSQL, but this project currently uses MySQL/H2 paths.
- Elasticsearch/OpenSearch vector search: useful when keyword/full-text search is the main product, but heavier and less focused for this RAG MVP.
- Chroma: convenient for Python experiments, but less suitable as the backend-owned production service for this Spring Boot project.

## Data Flow

Stage 1 flow:

1. `km-ingest-service` accepts a local file path and optional `docId`.
2. Service stores raw file as `raw/{docId}/source.{ext}` in MinIO.
3. Service runs MinerU locally with `pipeline` backend into a work output directory.
4. Service normalizes/copies output artifacts to `content.md`, `middle.json`, `layout.json`, and image paths where possible.
5. Service uploads parsed artifacts to `parsed/{docId}/...` in MinIO.
6. Service returns/writes a manifest JSON for later Spring Boot/database integration.

Later target flow:

1. `DocumentServiceImpl.uploadDocument` validates and creates `docId`.
2. Raw file is stored as `raw/{docId}/source.{ext}`.
3. Document is inserted with status `UPLOADED` or `PROCESSING` depending on synchronous/asynchronous choice.
4. Processor retrieves raw file stream and sends it to MinerU/service.
5. Processor stores MinerU artifacts to `parsed/{docId}/...`.
6. Processor converts MinerU blocks/markdown into chunks with page/block/bbox provenance.
7. Processor inserts DB chunks in a transaction after deleting stale chunks for retries.
8. Processor calls embedding/vector upsert with chunk IDs and metadata.
9. Document status becomes `READY`; failure sets `FAILED` and preserves raw file for retry.

## Contracts

### MinerU Adapter

- Input: document ID, filename, MIME type, raw file stream or raw object path.
- Output: markdown bytes/string, optional `middle.json`, optional `layout.json`, page image streams/bytes, and normalized block metadata.
- Errors must be mapped to a concise failure reason for `document.error_msg`.
- For stage 1, the adapter can be a Python CLI wrapper rather than a Spring Boot adapter.

### Chunk Provenance

- One chunk can reference multiple block IDs.
- `bbox` is stored as JSON array aligned to the referenced blocks or as an aggregate list of boxes.
- `page_no` is the primary page used for citation preview; multi-page chunks should either store the first page or a page range in artifacts JSON in a later extension.

### Search Responses

- Existing `SearchResultItemVO` should be extended only if frontend needs citation metadata now.
- Recommended fields: `pageNo`, `sourceMdPath`, `blockIds`, `bbox`.
- Fallback LIKE search should return the same citation fields from DB chunks.

## Compatibility

- Additive database migrations are preferred over destructive column changes.
- Existing `document.file_path` can continue to mean raw object path in the first migration.
- Existing local storage implementation can use the same object names under the configured root for development.
- Existing parser config should allow `mineru` and add connection fields only after the MinerU invocation contract is chosen.

## Tradeoffs

- Storing chunk content in DB duplicates text from Markdown but enables fast hover references and fallback search.
- Storing full Markdown in MinIO avoids large DB rows and preserves complete parser output.
- A Python CLI wrapper around MinerU is the fastest stage-1 path; a local HTTP MinerU service is cleaner later when Spring Boot calls parsing remotely.
- Qdrant adds one extra service, but keeps vector search purpose-built and avoids forcing a relational database migration.

## Rollback Shape

- If parsing fails, keep raw file and document row with `FAILED` status.
- Retrying should delete old chunks, vector entries, and parsed artifacts for the doc before reprocessing.
- Additive schema changes can remain unused if the parser feature is disabled.
