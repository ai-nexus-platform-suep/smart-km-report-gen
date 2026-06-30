# Plan MinerU document parsing and RAG storage

## Goal

Implement the first stage of a MinerU-based knowledge-base build pipeline as a standalone Python service beside the Spring Boot modules. Stage 1 stores uploaded files in MinIO, runs MinerU, and stores Markdown/JSON/image parser artifacts back to MinIO. Later stages will split chunks, persist citation metadata, and write vectors.

## Background

- User-provided storage split:
  - MinIO stores large files and parser artifacts.
  - Database stores searchable and relational business metadata.
  - Vector service stores embeddings and minimal recall metadata.
- Required MinIO object layout:
  - Raw source: `raw/{doc_id}/source.pdf`
  - MinerU Markdown: `parsed/{doc_id}/content.md`
  - MinerU JSON: `parsed/{doc_id}/middle.json` and `parsed/{doc_id}/layout.json`
  - Page images: `parsed/{doc_id}/images/page_1.png`
- Required chunk data shape:
  - `chunk_id`, `doc_id`, `content`, `source_md_path`, `page_no`, `block_ids`, `bbox`
  - `chunk_id` maps to one or more MinerU `block_id` values.
- Vector database recommendation for the student-team MVP: use Qdrant as the vector recall engine, while keeping citation/source-of-truth data in MySQL and large artifacts in MinIO.
- Qdrant does not strictly require Docker, but the recommended MVP deployment is Docker Compose so every team member can start the same local vector database with one command.
- New user scope for this iteration: create a Python knowledge-base build service as a sibling directory to `km-backend`, focused only on file ingestion to MinIO and MinerU artifact generation to MinIO.
- Existing repository has `km-ai-service/README.md` describing a future Python service, but no Python service code yet.
- Existing root `docker-compose.yml` includes MinIO and Milvus, but Docker is not available in the current shell.
- Local Python is available as Python 3.12.1, which is within MinerU's Windows-supported Python 3.10-3.12 range.
- MinerU official documentation supports CLI/API/Docker usage and `mineru -p <input_path> -o <output_path> -b pipeline` for CPU-friendly parsing.
- MinerU models can be downloaded with `mineru-models-download`; after download, models can be moved and configured through `mineru.json`, and `MINERU_MODEL_SOURCE=local` can force local model use.
- Existing repository facts:
  - `document.file_path` exists and currently stores the uploaded object path (`km-backend/src/main/resources/db/migration/V1__init_km.sql:23`).
  - `chunk.content` and `chunk.vector_id` exist (`km-backend/src/main/resources/db/migration/V1__init_km.sql:40`).
  - `chunk` currently lacks `source_md_path`, `page_no`, `block_ids`, and `bbox`.
  - Upload currently stores one object at `{kbId}/{docId}/{filename}` and creates a document in `UPLOADED` status (`km-backend/src/main/java/com/km/service/impl/DocumentServiceImpl.java:71`).
  - Parser config exists but defaults to `tika` and only validates `tika|native` (`km-backend/src/main/java/com/km/dto/request/ParserConfigRequest.java:12`).
  - No MinerU parser implementation or processing pipeline exists in the backend source tree.

## Requirements

- Stage 1 must create a standalone Python directory beside Spring Boot modules, recommended name `km-ingest-service`.
- Stage 1 must provide a simple callable interface for file ingestion: local file path in, `doc_id` generated or supplied, MinIO raw and parsed artifact paths out.
- Store original uploaded documents in MinIO under `raw/{doc_id}/source.<ext>`.
- Invoke MinerU for supported documents and persist the complete parser output artifacts to MinIO under `parsed/{doc_id}/...`.
- Stage 1 must not require installing MinerU, virtualenvs, pip cache, or model files on `C:` if an alternate project/disk path is available.
- Use project-local paths by default: `.venv` inside the new service, `.cache/pip`, `.cache/huggingface`, `.cache/modelscope`, `.cache/mineru`, and `.mineru/mineru.json` under the new service or a configured non-C path.
- Prefer MinerU `pipeline` backend for initial CPU-compatible local development.
- Persist parsed Markdown path and parser artifact paths in returned metadata; database persistence can wait for later stages unless needed for the stage-1 demo.
- Split MinerU Markdown/block output into RAG chunks and store chunk text in the database as the authoritative business source for hover citations and fallback search.
- Store chunk source metadata in the database: `page_no`, `block_ids`, `bbox`, `source_md_path`, and the `chunk_id` to `block_id` mapping.
- Send chunk embeddings to the vector service with `chunk_id`, `doc_id`, `page_no`, embedding vector, and only minimal preview metadata.
- Do not treat the vector store as the source of truth for chunk content or citation metadata.
- Use Qdrant for MVP vector storage unless the team explicitly chooses a lower-service-count alternative.
- Keep Qdrant payload metadata small: `chunk_id`, `doc_id`, `kb_id`, `page_no`, and optional short preview text only.
- Make delete/retry behavior clean up or overwrite MinIO artifacts, DB rows, and vector entries consistently.
- Keep the first implementation aligned with the existing Spring Boot/MyBatis/MinIO abstractions and avoid broad rewrites of upload/search APIs.

## Acceptance Criteria

- [ ] A new Python service directory exists beside `km-backend` and contains clear setup/run instructions.
- [ ] The Python service can upload a local supported file to MinIO at `raw/{doc_id}/source.<ext>`.
- [ ] The Python service can run MinerU against the uploaded/local source file and collect generated Markdown/JSON/image outputs.
- [ ] A successful MinerU parse stores `content.md`, `middle.json`, `layout.json`, and page images under `parsed/{doc_id}/...` when MinerU produces them.
- [ ] The service returns or prints a manifest containing `doc_id`, raw object path, Markdown path, JSON paths, and image object paths.
- [ ] Setup instructions keep virtualenv, pip cache, model cache, and MinerU config off `C:` when possible.
- [ ] If MinerU is not installed yet, the plan identifies the exact install/download commands and target non-C paths before execution.
- [ ] Each persisted chunk includes chunk text plus source trace fields: `source_md_path`, `page_no`, `block_ids`, and `bbox`.
- [ ] Search result and chunk-list responses can include enough citation data for frontend hover references without reading the full Markdown from MinIO.
- [ ] Vector upsert payloads contain `chunk_id`, `doc_id`, `page_no`, embedding, and optional preview text only.
- [ ] Deleting a document removes raw file, parsed artifacts, DB chunks, and corresponding vector entries, or reports any cleanup failure safely.
- [ ] If MinerU fails, document status becomes `FAILED` with a useful error message and raw file remains available for retry.
- [ ] Implementation has focused validation for schema mapping, parser output mapping, and one happy-path document pipeline.

## Out of Scope

- Frontend citation UI implementation beyond returning the needed metadata.
- Replacing the existing AI/vector service API unless it lacks required upsert/delete contracts.
- Full OCR accuracy tuning or MinerU model optimization.
- Treating MinIO Markdown as the primary source for runtime hover text.
- Storing full chunk content, block mappings, or full citation payloads only in the vector database.
- Stage 1 excludes chunk splitting, DB writes, Spring Boot integration, and vector database writes unless explicitly approved after the ingest/parse artifact flow works.

## Open Questions

- What is the MinerU invocation contract for this project: local CLI, local HTTP service, or remote service? Recommended answer: local HTTP service with configurable base URL and timeout, because it fits the existing `KmAiClient` pattern and avoids blocking JVM threads on CLI process management. If using CLI instead, implementation needs process isolation, filesystem staging, timeout handling, and stronger cleanup logic.
- Should Qdrant be accepted as the MVP vector database? Recommended answer: yes for a graduation-project student team because it is simpler to deploy than Milvus, more purpose-built than Elasticsearch/OpenSearch for vector recall, and avoids changing the existing MySQL database into PostgreSQL just to use pgvector. Use Docker Compose for local/dev deployment unless the team cannot run Docker.
- Should implementation start with the standalone Python stage-1 ingest/parser service now? Recommended answer: yes, but only after review/start approval; implement `km-ingest-service` first, install MinerU into a project-local virtualenv, configure caches under the service directory or another non-C path, and stop before DB/chunk/vector work.
