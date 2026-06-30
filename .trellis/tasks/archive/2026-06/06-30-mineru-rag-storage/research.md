# Research: Existing Document And RAG Code

## Confirmed Facts

- `document` table currently has `file_path`, status fields, tags, and timestamps but no parsed artifact paths.
- `chunk` table currently has `content`, `chapter_path`, `chunk_index`, `chunk_type`, and `vector_id` but no page, block, bbox, or markdown source fields.
- `DocumentServiceImpl.uploadDocument` stores uploads at `{kbId}/{docId}/{filename}` and inserts a document with status `UPLOADED`.
- `DocumentServiceImpl.deleteDocument` deletes only `document.file_path`, DB chunks, and the document row.
- `SearchServiceImpl` converts vector hits into search results and falls back to DB keyword search using `chunk.content`.
- `SearchResultItemVO` currently lacks citation fields beyond `chunkId`, `documentId`, `chapterPath`, `content`, and scores.
- `system_config` seeds parser config with `backend=tika`, and `ParserConfigRequest` only allows `tika|native`.
- No MinerU-specific code, parser adapters, async document processing workers, or vector upsert code were found in the backend source tree.
- The current project uses MySQL/H2 schema paths, so pgvector would require introducing PostgreSQL rather than extending the existing database stack.
- The repository already has `km-ai-service/README.md` describing a planned Python service, but the directory currently contains only README documentation.
- Root `docker-compose.yml` already defines MinIO, MySQL, Redis, RabbitMQ, and Milvus services. Docker is not available in the current shell.
- Local Python is available as Python 3.12.1.
- MinerU documentation says Windows supports Python 3.10-3.12, `mineru[all]` can be installed with pip/uv, and CPU parsing can use `mineru -p <input_path> -o <output_path> -b pipeline`.
- MinerU model source can be set with `MINERU_MODEL_SOURCE`; local model usage can be forced with `MINERU_MODEL_SOURCE=local` after downloading/moving models and configuring `mineru.json`.
- Local validation found this machine's MinIO Console on `localhost:9000` and S3 API on `localhost:9005`.
- With the user-provided MinIO credentials in `km-ingest-service/.env`, `--skip-mineru` raw upload succeeded for `raw/smoke_test/source.txt`.
- Full MinerU pipeline validation succeeded for generated sample PDF doc ID `mineru_smoke_pdf`, uploading `raw/mineru_smoke_pdf/source.pdf`, `parsed/mineru_smoke_pdf/content.md`, and `parsed/mineru_smoke_pdf/middle.json`.

## Planning Implications

- The requested architecture requires additive schema changes and a new parser-processing service; it is not a small storage-path-only change.
- The current vector service client supports embed/search/rerank/delete health-like operations but no visible backend-side indexing pipeline.
- Search response and chunk response models need provenance fields if the frontend will render hover citations directly from API responses.
- The existing `FileStorageService` abstraction is suitable for both raw and parsed artifacts if object naming is centralized.
- Qdrant is the recommended MVP vector database because it keeps vector recall separate from MySQL business metadata while staying lightweight enough for a small student team.
- For the user's requested next increment, a standalone Python service is lower risk than immediate Spring Boot integration because it proves MinIO + MinerU artifact generation before DB/chunk/vector work.
- Stage 1 has been proven end-to-end on a simple generated PDF; missing `layout.json` and images are acceptable when MinerU does not generate them for the sample.
