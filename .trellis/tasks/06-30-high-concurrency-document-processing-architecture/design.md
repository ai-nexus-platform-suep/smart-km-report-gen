# High Concurrency Document Processing Design

## Decision

Use a split architecture: Java `km-backend` owns public APIs, persistence, status, and queue production; Python `km-ai-service` owns document parsing, MinerU execution, chunking, embedding, vector/chunk writes, and queue consumption.

This keeps synchronous request handling separate from long-running model-heavy workloads and matches existing repository direction in `docs/知识管理组-中间件与服务方案.md` and `docs/ai-service-contract.yaml`.

## Boundaries

- Java `km-backend`:
  - Accepts `POST /api/knowledge-bases/{kbId}/documents`.
  - Validates file, stores the raw object, creates or updates `document` metadata.
  - Publishes one document-processing job to RabbitMQ after the database transaction commits.
  - Exposes document list/detail/status and retry APIs.
  - Accepts internal status callbacks or consumes worker result messages.
- Python `km-ai-service`:
  - Consumes document-processing jobs from RabbitMQ.
  - Downloads the raw file from MinIO.
  - Runs MinerU or parser pipeline under bounded concurrency.
  - Produces normalized markdown, JSON, images, chunks, embeddings, and Milvus writes.
  - Reports phase transitions and final success/failure to Java.
- `km-ingest-service`:
  - Treat as a prototype/source of reusable behavior: MinerU command execution, artifact collection, MinIO artifact naming, and manifest shape.
  - Do not keep it as the production high-concurrency service once `km-ai-service` worker capability exists.

## Data Flow

1. Client uploads document to Java.
2. Java stores raw file under a stable object key and inserts document metadata with `UPLOADED`.
3. Java publishes a RabbitMQ message containing document identity, knowledge-base identity, raw object key, MIME type, file name, parser/chunk strategy, and attempt metadata.
4. Python consumer receives the message and updates status to `PARSING`.
5. Python runs MinerU and uploads parsed artifacts under `parsed/{documentId}/...`.
6. Python chunks content, embeds chunks, writes Milvus vectors, and writes or requests Java to write chunk metadata.
7. Python marks document `READY` with chunk count, parsed artifact references, and vector identifiers; on error it marks `FAILED` with a normalized error message.

## Queue Contract

Recommended exchange/queue shape:

- Exchange: `km.document.processing` as a durable direct exchange.
- Queue: `km.document.processing.parse` as a durable queue bound with routing key `document.process`.
- Dead-letter exchange: `km.document.processing.dlx`.
- Dead-letter queue: `km.document.processing.dlq`.
- Message key: `documentId` for idempotency and log correlation.

Minimum payload fields:

- `jobId`: unique delivery/job identifier.
- `documentId`: Java document primary key.
- `kbId`: knowledge-base identifier.
- `rawObject`: MinIO raw object path.
- `filename`: original filename.
- `mimeType`: uploaded MIME type.
- `parserBackend`: default `mineru` or `pipeline` depending final naming.
- `chunkStrategy`: resolved strategy used for chunking.
- `attempt`: retry attempt count.
- `callbackUrl`: optional Java internal status callback.
- `createdAt`: job creation timestamp.

## Status Updates

Use Java as the source of truth for document status. Python may update Java through one of two internal mechanisms:

- Preferred MVP: internal HTTP callback to Java, because it is easier to validate locally and matches `docs/ai-service-contract.yaml:189`.
- Scalable option: result/status RabbitMQ queue consumed by Java, reducing synchronous coupling from Python to Java.

The Java status API should reject stale updates by checking document identity and, if available, attempt number. Final states are terminal for a given attempt unless retry explicitly creates a new attempt.

## Concurrency And Backpressure

High concurrency should be controlled at the worker layer, not by spawning one parser process per upload request.

- Java returns immediately after enqueueing and does not wait for parsing.
- RabbitMQ buffers workload and controls delivery through consumer prefetch.
- Python worker exposes configurable `MAX_CONCURRENT_PARSE_JOBS` and `RABBITMQ_PREFETCH_COUNT`; first implementation defaults both to a conservative `1-2` range for local MVP stability.
- MinerU execution runs under a bounded process/semaphore pool.
- Heavy jobs should respect temp/work directory isolation per `documentId`.
- Worker scale-out is horizontal: add Python worker replicas when CPU/GPU/memory capacity allows.
- Throughput tuning is deferred until smoke tests establish baseline stability; concurrency should increase through configuration and worker replicas rather than Java request-thread work.

## Idempotency And Retries

- Use `documentId` as the idempotency key for chunk/vector output.
- Before writing final chunks/vectors, remove or overwrite previous outputs for the same `documentId` within the same retry attempt.
- RabbitMQ retry policy should limit attempts and route exhausted jobs to DLQ.
- Retry from Java should reset status to `UPLOADED` or `PARSING`, increment attempt metadata if introduced, and publish a new job.
- Failure messages shown to users should be normalized and bounded in length.

## Compatibility

- Keep current public document endpoints stable.
- Reuse existing Java storage abstraction where possible; note that local storage is enabled in `application-dev.yml:29`, while production/high-concurrency processing should use MinIO so Python workers can read source objects.
- Existing `km-ingest-service` object naming can be preserved for parsed artifacts: `raw/{documentId}/source.<ext>` and `parsed/{documentId}/...`.
- If Java currently stores object keys as `{kbId}/{docId}/{filename}`, either standardize the key for new uploads or include the stored key in the job payload.

## Trade-Offs

- Java-only parsing reduces service count but couples request handling to Python CLI/model workloads, making high concurrency riskier.
- Python public ingestion API is simpler short-term but creates two public API surfaces and bypasses Java-owned metadata/status rules.
- RabbitMQ adds operational complexity but provides buffering, retries, backpressure, and independent worker scaling.
- HTTP status callbacks are simple; result queues are more robust at scale. The design can start with HTTP callbacks and evolve to result queues without changing the public API.

## Rollback Shape

- If worker processing is unhealthy, Java can keep accepting uploads as `UPLOADED` while queue publishing is disabled or routed to DLQ, but user-visible status must clearly show pending or failed processing.
- The previous prototype path in `km-ingest-service` can remain available for manual smoke tests until `km-ai-service` reaches parity.
