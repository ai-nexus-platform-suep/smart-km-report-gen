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
- Raw object: `raw/{doc_id}/source.<ext>`
- Parsed Markdown object: `parsed/{doc_id}/content.md`
- Parsed JSON objects: `parsed/{doc_id}/middle.json`, `parsed/{doc_id}/layout.json`
- Parsed images: `parsed/{doc_id}/images/page_<n>.<ext>`

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
- Sample parse test asserts normalized artifact discovery and upload paths for Markdown, JSON, and images.

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
