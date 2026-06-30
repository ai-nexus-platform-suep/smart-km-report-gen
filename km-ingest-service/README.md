# km-ingest-service

Python ingest service for the first stage of knowledge-base construction.

Stage 1 scope:

- upload a local source file to MinIO as `raw/{doc_id}/source.<ext>`
- run MinerU locally
- upload MinerU artifacts to `parsed/{doc_id}/...`
- print/write a manifest JSON

Out of scope for this stage: database writes, chunk splitting, vector database writes, and Spring Boot integration.

## Local Setup

Run commands from the repository root.

```powershell
$env:PIP_CACHE_DIR = "$PWD\km-ingest-service\.cache\pip"
$env:TEMP = "$PWD\km-ingest-service\.tmp"
$env:TMP = "$PWD\km-ingest-service\.tmp"
$env:HF_HOME = "$PWD\km-ingest-service\.cache\huggingface"
$env:HUGGINGFACE_HUB_CACHE = "$PWD\km-ingest-service\.cache\huggingface\hub"
$env:MODELSCOPE_CACHE = "$PWD\km-ingest-service\.cache\modelscope"
$env:MINERU_TOOLS_CONFIG_JSON = "$PWD\km-ingest-service\.mineru\mineru.json"
$env:MINERU_MODEL_SOURCE = "modelscope"

python -m venv km-ingest-service\.venv
km-ingest-service\.venv\Scripts\python -m pip install --upgrade pip
km-ingest-service\.venv\Scripts\python -m pip install -r km-ingest-service\requirements.txt
```

The virtual environment, pip cache, and temporary build directory stay under `km-ingest-service/` so they do not intentionally download to `C:`.

## MinerU Models

MinerU can download models on first use. If you want to download explicitly:

```powershell
$env:PIP_CACHE_DIR = "$PWD\km-ingest-service\.cache\pip"
$env:TEMP = "$PWD\km-ingest-service\.tmp"
$env:TMP = "$PWD\km-ingest-service\.tmp"
$env:HF_HOME = "$PWD\km-ingest-service\.cache\huggingface"
$env:HUGGINGFACE_HUB_CACHE = "$PWD\km-ingest-service\.cache\huggingface\hub"
$env:MODELSCOPE_CACHE = "$PWD\km-ingest-service\.cache\modelscope"
$env:MINERU_TOOLS_CONFIG_JSON = "$PWD\km-ingest-service\.mineru\mineru.json"
$env:MINERU_MODEL_SOURCE = "modelscope"
km-ingest-service\.venv\Scripts\mineru-models-download
```

If MinerU writes `mineru.json` under your user directory, copy it to `km-ingest-service/.mineru/mineru.json` and set `MINERU_TOOLS_CONFIG_JSON` to the project-local file.

## Environment

Copy `km-ingest-service/.env.example` to `km-ingest-service/.env` or pass variables in the shell.
Leave `MINERU_COMMAND` unset when running with `.venv\Scripts\python`; the CLI auto-detects the venv-local MinerU executable.

When using the root `docker-compose.yml` MinIO service from the host machine, defaults are:

- endpoint: `localhost:9000`
- access key: `minioadmin`
- secret key: `minioadmin`
- bucket: `km-documents`

Use the S3 API port, not the MinIO Console port. If `http://localhost:9000` shows the MinIO Console HTML, set `MINIO_ENDPOINT` to the actual S3 API port such as `localhost:9005`.

## Run

Run the CLI from `km-ingest-service/` so Python can resolve the local `app` package.

```powershell
cd km-ingest-service
.venv\Scripts\python -m app ingest --file D:\path\sample.pdf --doc-id doc_123 --manifest .work\doc_123\manifest.json
```

The command prints the manifest to stdout and optionally writes it to `--manifest`.

Run the HTTP API from `km-ingest-service/` with Uvicorn:

```powershell
cd km-ingest-service
.venv\Scripts\uvicorn app.api:app --host 0.0.0.0 --port 8093 --reload
```

The API exposes `GET /health` and `POST /ingest` with multipart `file`, optional form `doc_id`, and optional form `skip_mineru`.
`doc_id` is generated when omitted. When provided, use 1-128 characters with letters, numbers, dot, underscore, or hyphen; it must start with a letter or number.

## Manifest Shape

```json
{
  "doc_id": "doc_123",
  "raw_object": "raw/doc_123/source.pdf",
  "markdown_object": "parsed/doc_123/content.md",
  "json_objects": {
    "middle": "parsed/doc_123/middle.json",
    "layout": "parsed/doc_123/layout.json"
  },
  "image_objects": ["parsed/doc_123/images/page_1.png"],
  "parser_backend": "pipeline",
  "error": null
}
```
