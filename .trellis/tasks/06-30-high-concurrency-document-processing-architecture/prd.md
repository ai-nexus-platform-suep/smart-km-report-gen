# High concurrency document processing architecture

## Goal

Define the high-concurrency document processing architecture for knowledge-base ingestion so uploads stay responsive, parser workloads scale independently, and the existing Java-facing API remains the stable integration point for other teams.

## Background

- `km-backend` is the business-facing Java service for knowledge-base, document, search, and status APIs; `README.md:11` and `docs/知识管理组-中间件与服务方案.md:14` identify it as the main REST/business orchestration service.
- `km-ai-service` is intended as a Python parallel service for chunking, embedding, rerank, Milvus access, and document processing workers; `README.md:13`, `km-ai-service/README.md:3`, and `docs/知识管理组-中间件与服务方案.md:15` describe this split.
- The documented ingestion pipeline is asynchronous: Java upload writes MinIO/MySQL and sends RabbitMQ, Python consumes, processes, writes vectors/chunks, and Java exposes status; see `docs/知识管理组-中间件与服务方案.md:111`.
- The internal AI service contract already marks document processing as async recommended and allows RabbitMQ consumer or Java-triggered HTTP; see `docs/ai-service-contract.yaml:43`.
- Current Java upload flow stores files and document metadata synchronously in `km-backend/src/main/java/com/km/service/impl/DocumentServiceImpl.java:64`, then returns `DocumentUploadResponse`; it does not yet publish processing jobs.
- RabbitMQ, Redis, MinIO, and Milvus are available in `docker-compose.yml:26`, `docker-compose.yml:40`, `docker-compose.yml:58`, and `docker-compose.yml:77`; Java has AMQP/Redis dependencies in `km-backend/pom.xml:77` and `km-backend/pom.xml:81`.
- Current MinerU integration lives in the standalone Python `km-ingest-service`: it runs the MinerU CLI in `km-ingest-service/app/mineru_runner.py:10` and orchestrates artifact upload in `km-ingest-service/app/pipeline.py:18`.

## Requirements

- R1: Keep all public document-upload and status/query APIs owned by Java `km-backend`; external callers must not call Python directly for document ingestion.
- R2: Make document parsing, chunking, embedding, and vector writes asynchronous from upload response latency.
- R3: Use RabbitMQ as the primary work-dispatch mechanism between Java and Python for high-concurrency document processing.
- R4: Keep Python responsible for MinerU execution and AI/model-heavy processing so parser dependencies, model caches, CPU/GPU pressure, and worker scaling are isolated from Java request handling.
- R5: Persist document processing state in Java-owned metadata so users can observe `UPLOADED`, `PARSING`, `CHUNKING`, `EMBEDDING`, `READY`, and `FAILED` states.
- R6: Ensure each processing job is idempotent or safely retryable by `documentId`, with duplicate job delivery not creating duplicate chunks/vectors.
- R7: Support worker-side concurrency limits and backpressure so high upload concurrency does not spawn unbounded MinerU processes.
- R8: Preserve the current MinIO object-store boundary: Java stores original files; Python reads source objects and writes parsed artifacts and vector/chunk outputs.
- R9: Plan the transition from `km-ingest-service` as a standalone prototype into `km-ai-service` worker capability, without requiring Java to shell out to MinerU directly.
- R10: Provide a verification path that proves upload returns quickly, queue delivery happens, processing status advances, and failed processing is observable.
- R11: Optimize the first implementation for local MVP stability with horizontal scale-out hooks: default one Python worker to `1-2` concurrent MinerU jobs and RabbitMQ prefetch `1-2`, then tune after smoke tests or load tests.

## Acceptance Criteria

- [ ] The approved design keeps Java as the public API/orchestrator and Python as the parser/AI worker boundary.
- [ ] The design specifies RabbitMQ message ownership, payload fields, retry/dead-letter behavior, and status update flow.
- [ ] The design specifies how concurrency is controlled for MinerU/process-heavy workloads.
- [ ] The implementation plan includes Java producer/status changes, Python worker changes, observability, and validation commands.
- [ ] The plan identifies how existing `km-ingest-service` behavior maps into `km-ai-service` or is retired.
- [ ] The plan includes at least one integration smoke test path covering upload-to-ready or upload-to-failed processing.
- [ ] The first implementation defaults to conservative worker concurrency while keeping settings configurable for later scale-out.

## Out of Scope

- Implementing the queue, worker, or API changes during planning.
- Replacing MinerU with a Java-native parser such as PDFBox/Tika.
- Exposing Python ingestion endpoints as public APIs for other groups.
- Full production autoscaling rules for Kubernetes/Nacos beyond the service boundary and concurrency model needed for this repository.

## Decisions

- Initial capacity target: local MVP stability with scale-out hooks. Use conservative defaults of `1-2` concurrent MinerU jobs per Python worker and RabbitMQ prefetch `1-2`; raise throughput later through worker replicas and measured tuning.
