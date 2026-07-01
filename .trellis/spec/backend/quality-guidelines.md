# Quality Guidelines

> Code quality standards for backend development.

---

## Overview

<!--
Document your project's quality standards here.

Questions to answer:
- What patterns are forbidden?
- What linting rules do you enforce?
- What are your testing requirements?
- What code review standards apply?
-->

(To be filled by the team)

---

## Forbidden Patterns

<!-- Patterns that should never be used and why -->

(To be filled by the team)

---

## Required Patterns

<!-- Patterns that must always be used -->

(To be filled by the team)

---

## Testing Requirements

<!-- What level of testing is expected -->

(To be filled by the team)

---

## Code Review Checklist

<!-- What reviewers should check -->

(To be filled by the team)

---

## Scenario: Python Ingest Service For MinerU Artifacts

### 1. Scope / Trigger

- Trigger: adding or changing `km-ingest-service`, the Python boundary that uploads raw files to MinIO, runs MinerU, uploads parser artifacts, and emits a manifest.
- This is an infra/cross-layer contract because it touches local files, MinIO object paths, MinerU CLI/model config, environment variables, and future Spring Boot/database integration.

### 2. Signatures

- CLI: `python -m app ingest --file <local_path> [--doc-id <doc_id>] [--manifest <path>] [--skip-mineru]`
- HTTP health: `GET /health` -> `{"status":"ok"}`
- HTTP ingest: `POST /ingest` multipart form with `file`, optional `doc_id`, optional `skip_mineru`
- Raw object: `raw/kb-{kbName}--{shortKbId}/{filename}--doc-{documentId}.<ext>` for Java-owned uploads.
- Parsed Markdown object: `parsed/kb-{kbName}--{shortKbId}/{filename}--doc-{documentId}/content.md`.
- Parsed JSON objects: `parsed/kb-{kbName}--{shortKbId}/{filename}--doc-{documentId}/middle.json`, `parsed/kb-{kbName}--{shortKbId}/{filename}--doc-{documentId}/layout.json`.
- Parsed images: do not upload image files/directories for the local MVP; strip image references from normalized Markdown.

### 3. Contracts

- Required input: `--file` must point to an existing local file.
- Required HTTP input: multipart `file` upload; the API stages it under `KM_INGEST_SERVICE_WORK_DIR`/`.work`, not system temp paths where controllable.
- Optional input: `--doc-id` or form `doc_id`; generate a UUID hex string when omitted.
- `doc_id` constraint: 1-128 characters, starts with a letter or number, and may contain only letters, numbers, dot, underscore, or hyphen.
- Optional HTTP input: form `skip_mineru`; defaults to `false`; use `true` for MinIO-only smoke tests.
- Response/manifest fields: `doc_id`, `raw_object`, `markdown_object`, `json_objects`, `image_objects`, `parser_backend`, `error`.
- Required MinIO env keys: `MINIO_ENDPOINT`, `MINIO_ACCESS_KEY`, `MINIO_SECRET_KEY`, `MINIO_BUCKET`.
- Optional MinIO env key: `MINIO_SECURE`; infer secure mode when `MINIO_ENDPOINT` includes `https://`.
- Optional service env keys: `KM_INGEST_SERVICE_URL`, `KM_INGEST_SERVICE_WORK_DIR`.
- MinerU env keys should stay project-local where possible: `PIP_CACHE_DIR`, `TEMP`, `TMP`, `HF_HOME`, `HUGGINGFACE_HUB_CACHE`, `MODELSCOPE_CACHE`, `MINERU_TOOLS_CONFIG_JSON`, `MINERU_MODEL_SOURCE`.
- Queue payload should include `kbName` in addition to `kbId` so Python artifacts can preserve the same human-readable knowledge-base prefix as Java raw uploads.

### 4. Validation & Error Matrix

- Missing local file -> raise `FileNotFoundError` before MinIO or MinerU calls.
- Missing HTTP `file` form field -> FastAPI returns request validation error.
- Invalid `doc_id` -> raise `ValueError`; HTTP endpoint maps it to `400` before MinIO or MinerU work.
- MinIO Console endpoint used instead of S3 API port -> fail with a message telling the user to use the S3 API port.
- Wrong MinIO credentials -> fail with a message telling the user to check `MINIO_ACCESS_KEY` / `MINIO_SECRET_KEY`.
- Missing MinerU command -> return a manifest with `error` explaining that MinerU dependencies must be installed in `.venv`.
- MinerU exits non-zero -> return a manifest with `raw_object` preserved and `error` populated.
- Missing optional MinerU outputs -> omit the corresponding manifest object path instead of crashing.

### 5. Good/Base/Bad Cases

- Good: `MINIO_ENDPOINT=localhost:9005`, valid credentials, installed pipeline models, PDF input -> raw and parsed objects uploaded, manifest has all generated paths.
- Base: `--skip-mineru` with valid MinIO -> raw object uploaded, parsed fields empty, useful for MinIO smoke tests.
- Base: `POST /ingest` with multipart `file`, `doc_id=api_smoke_test`, `skip_mineru=true` -> HTTP `200` manifest with `raw/api_smoke_test/source.<ext>` and empty parsed fields.
- Bad: `MINIO_ENDPOINT=localhost:9000` when 9000 is the Console -> upload fails before object creation with endpoint guidance.
- Bad: `doc_id=../bad` -> HTTP `400` and no work-dir deletion outside the configured work directory.

### 6. Tests Required

- `python -m compileall app` asserts Python syntax/import validity.
- `python -m app --help` asserts CLI registration.
- `mineru --help` asserts the venv-local MinerU executable is available.
- MinIO smoke test with `--skip-mineru` asserts bucket creation/upload and manifest writing.
- FastAPI smoke test asserts `GET /health`, `/openapi.json`, `POST /ingest` with `skip_mineru=true`, and invalid `doc_id` returning `400`.
- Sample parse test asserts normalized artifact discovery and upload paths for Markdown and JSON only.

### 7. Wrong vs Correct

#### Wrong

```powershell
$env:MINIO_ENDPOINT = "localhost:9000"  # 9000 is the MinIO Console in this environment
.venv\Scripts\python -m app ingest --file sample.pdf --doc-id doc_1
```

#### Correct

```powershell
$env:MINIO_ENDPOINT = "localhost:9005"  # S3 API port
$env:MINERU_TOOLS_CONFIG_JSON = "$PWD\.mineru\mineru.json"
.venv\Scripts\python -m app ingest --file sample.pdf --doc-id doc_1
```

#### Wrong

```powershell
curl.exe -F "file=@sample.pdf" -F "doc_id=../bad" http://localhost:8093/ingest
```

#### Correct

```powershell
.venv\Scripts\uvicorn app.api:app --host 0.0.0.0 --port 8093 --reload
curl.exe -F "file=@sample.pdf" -F "doc_id=doc_1" -F "skip_mineru=false" http://localhost:8093/ingest
```

## Scenario: Single Spring Boot Runtime YAML

### 1. Scope / Trigger

- Trigger: changing backend runtime configuration for `km-backend`.
- This is an infra contract because datasource, Redis, RabbitMQ, MinIO, JWT, AI service URL, and document-processing queue settings determine whether Java and Python can start and communicate locally.

### 2. Signatures

- Config file: `km-backend/src/main/resources/application.yml`.
- Normal backend start: `mvn -pl km-backend -am spring-boot:run`.
- Compile check: `mvn -pl km-backend -am compile`.

### 3. Contracts

- Keep one Spring Boot runtime YAML in `km-backend/src/main/resources/`: `application.yml`.
- Do not set `spring.profiles.active` in `application.yml` for local MVP startup.
- Edit MySQL credentials at `spring.datasource.username` and `spring.datasource.password`.
- Edit MinIO at `km.minio.*`, RabbitMQ at `spring.rabbitmq.*`, and queue names at `km.document-processing.queue.*`.
- Preserve `server.port`, `management.endpoints`, `spring.flyway`, `mybatis`, `km.storage`, `km.jwt`, and `km.ai-service` when refactoring config.

### 4. Validation & Error Matrix

- More than one `application*.yml` under backend resources -> fail review; merge back into `application.yml` unless a task explicitly reintroduces profiles.
- `spring.profiles.active` appears in `application.yml` -> fail review because it reintroduces hidden config indirection.
- MySQL `Access denied` on startup -> check `spring.datasource.username` and `spring.datasource.password` in `application.yml` first.
- MinIO connection warning on startup -> check `km.minio.endpoint`, `access-key`, `secret-key`, and `bucket` in `application.yml`.
- RabbitMQ connection failure -> check `spring.rabbitmq.host`, `port`, `username`, `password`, and `virtual-host` in `application.yml`.

### 5. Good/Base/Bad Cases

- Good: one `application.yml` contains datasource, Redis, RabbitMQ, Flyway, MyBatis, MinIO, JWT, AI service, and document-processing queue settings.
- Base: local developer changes only `spring.datasource.password` in `application.yml` to match their MySQL user.
- Bad: adding `application-dev.yml` and setting `spring.profiles.active: dev`, making developers edit the wrong file again.

### 6. Tests Required

- `glob km-backend/src/main/resources/application*.yml` returns only `application.yml`.
- Search confirms `spring.profiles.active` is absent from `application.yml`.
- `mvn -pl km-backend -am compile` passes when Maven is available.
- Backend smoke start should read `server.port: 8091` and expose Swagger at `/swagger-ui/index.html` when dependencies are reachable.

### 7. Wrong vs Correct

#### Wrong

```yaml
spring:
  profiles:
    active: dev
```

#### Correct

```yaml
spring:
  datasource:
    username: km
    password: km123456
```

## Scenario: MVP Request User Context

### 1. Scope / Trigger

- Trigger: backend APIs that create, upload, search, or otherwise write/query user-scoped data before JWT authentication is integrated.
- This is a cross-layer contract because database fields such as `knowledge_base.owner_id` and `document.created_by` must have a clear request source.

### 2. Signatures

- Request header: `userid: <positive-long>`.
- Resolver: `com.km.controller.support.RequestUserResolver.requireUserId(String headerValue)`.
- Affected MVP endpoints: `POST /api/knowledge-bases`, `POST /api/knowledge-bases/{kbId}/documents`, and `POST /api/search`.

### 3. Contracts

- Do not default user-scoped controller paths to `0L`.
- Require `userid` while JWT is not integrated.
- `userid` must parse as a positive Java `long`.
- Missing or invalid `userid` throws `BusinessException(ErrorCode.BAD_REQUEST, ...)` and returns HTTP 400 through the global exception handler.
- When JWT arrives, replace the resolver implementation or call site with authenticated principal extraction without changing service-layer persistence semantics.

### 4. Validation & Error Matrix

- Missing `userid` -> HTTP 400, message `Missing required header: userid`.
- Non-numeric `userid` -> HTTP 400, message `userid must be a positive integer`.
- `userid <= 0` -> HTTP 400, message `userid must be a positive integer`.
- Valid `userid` on KB create -> writes `knowledge_base.owner_id`.
- Valid `userid` on document upload -> writes `document.created_by`.

### 5. Good/Base/Bad Cases

- Good: `curl -H "userid: 1" -H "Content-Type: application/json" -d '{...}' http://localhost:8091/api/knowledge-bases`.
- Base: Swagger users manually add `userid` while testing MVP endpoints.
- Bad: controller silently falls back to `0L`, making ownership impossible to trace.

### 6. Tests Required

- Search controllers for `userId = 0L` and reject new fallback code in user-scoped paths.
- Call create/upload/search without `userid` and assert HTTP 400.
- Call create/upload with `userid: 1` and assert `owner_id` / `created_by` equals `1`.
- Run `mvn -pl km-backend -am compile` when Maven is available.

### 7. Wrong vs Correct

#### Wrong

```java
return ApiResponse.ok(knowledgeBaseService.create(request, 0L));
```

#### Correct

```java
Long userId = requestUserResolver.requireUserId(userIdHeader);
return ApiResponse.ok(knowledgeBaseService.create(request, userId));
```
