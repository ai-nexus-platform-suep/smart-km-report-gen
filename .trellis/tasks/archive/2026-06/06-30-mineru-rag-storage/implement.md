# Implementation Plan: MinerU RAG Storage Pipeline

## Stage 1 Scope

Build `km-ingest-service/` as a Python sibling service that uploads a local file to MinIO, runs MinerU, uploads generated Markdown/JSON/images to MinIO, and emits a manifest. Stop before DB chunk persistence, Spring Boot integration, Qdrant/Milvus writes, or frontend citation UI.

## Checklist

- Review and approve this stage-1 scope before running `task.py start` or installing MinerU.
- Create `km-ingest-service/` with project-local `.venv` setup instructions and dependency files.
- Configure environment variables for non-C installation/cache paths: `PIP_CACHE_DIR`, `HF_HOME`, `HUGGINGFACE_HUB_CACHE`, `MODELSCOPE_CACHE`, and MinerU config/cache paths where supported.
- Add MinIO settings and bucket/path conventions to `.env.example` or service-local `.env.example`.
- Implement MinIO upload helpers for raw file and parsed artifacts.
- Implement MinerU CLI wrapper using `mineru -p <input_path> -o <output_dir> -b pipeline`.
- Implement artifact discovery/normalization for Markdown, `middle.json`, `layout.json`, and page images.
- Implement a CLI entry point that returns/writes manifest JSON.
- Add README instructions for installing MinerU/models without intentionally using C drive.
- Confirm MinerU invocation contract and output schema before writing adapter code.
- Confirm Qdrant as the MVP vector database and define collection name, vector dimension, distance metric, and payload schema.
- Add a Docker Compose service for Qdrant unless the team decides not to use Docker.
- Add additive Flyway migration and H2 schema updates for document artifact metadata and chunk provenance fields.
- Update `Document`, `Chunk`, MyBatis result maps, inserts, batch inserts, and base columns.
- Normalize object naming so uploads use `raw/{docId}/source.{ext}` and parsed artifacts use `parsed/{docId}/...`.
- Add a MinerU adapter interface and implementation matching the confirmed invocation contract.
- Add a processing service that retrieves raw files, invokes MinerU, stores artifacts, creates chunks, upserts vectors, and updates status.
- Extend chunk/list/search response models with page and provenance fields needed by frontend citations.
- Update delete/retry paths to remove parsed artifacts, DB chunks, and vector entries consistently.
- Add focused tests or local validation fixtures for schema mapping and parser-output-to-chunk mapping.

## Validation Commands

- `python --version`
- `python -m venv km-ingest-service/.venv`
- `km-ingest-service/.venv/Scripts/python -m pip install -r km-ingest-service/requirements.txt`
- `km-ingest-service/.venv/Scripts/mineru --help`
- `km-ingest-service/.venv/Scripts/python -m app ingest --file <sample.pdf> --doc-id <doc_id>`
- Verify MinIO contains `raw/{docId}/source.<ext>` and `parsed/{docId}/...`.
- `mvn -pl km-backend -am test`
- `mvn -pl km-backend -am compile`
- Run the backend with H2 profile and upload a sample PDF through the document API.
- Verify MinIO/local storage contains `raw/{docId}/...` and `parsed/{docId}/...` artifacts.
- Verify DB chunk rows include `content`, `source_md_path`, `page_no`, `block_ids`, and `bbox`.
- Verify Qdrant points are keyed by chunk ID and contain only minimal metadata payload.
- Verify Qdrant starts locally through Docker Compose and exposes the configured HTTP port.

## Risky Files

- `km-ingest-service/**`
- `.env.example`
- `docker-compose.yml` if MinIO/Qdrant service definitions change
- `km-backend/src/main/resources/db/migration/*.sql`
- `km-backend/src/main/resources/schema-h2.sql`
- `km-backend/src/main/resources/mapper/ChunkMapper.xml`
- `km-backend/src/main/resources/mapper/DocumentMapper.xml`
- `km-backend/src/main/java/com/km/service/impl/DocumentServiceImpl.java`
- `km-backend/src/main/java/com/km/service/impl/SearchServiceImpl.java`

## Review Gates

- Review whether `km-ingest-service` is the accepted directory name.
- Review before installing MinerU/models because this can download large dependencies and models.
- Review actual MinerU output filenames after the first sample parse before hard-coding artifact discovery.
- Review schema field names before migration is finalized.
- Review MinerU adapter contract before implementing service integration.
- Review Qdrant payload schema before adding vector upsert/search/delete calls.
- Review whether processing should be synchronous MVP or async worker before changing upload behavior.

## Rollback Points

- Stage 1 can be rolled back by deleting `km-ingest-service/` and any MinIO test objects for the chosen `docId`.
- Schema migration can be stopped before application code consumes new columns.
- Parser service integration can be feature-gated behind `parser.backend=mineru`.
- Vector upsert can be disabled while keeping DB chunks and MinIO artifacts for debugging.
