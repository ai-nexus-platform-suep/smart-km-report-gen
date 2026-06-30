from __future__ import annotations

import os
from pathlib import Path
import shutil
import uuid

from dotenv import load_dotenv
from fastapi import FastAPI, File, Form, HTTPException, UploadFile

from .config import IngestConfig
from .pipeline import ingest_document


SERVICE_ROOT = Path(__file__).resolve().parents[1]
load_dotenv(SERVICE_ROOT / ".env")
LOCAL_TMP = SERVICE_ROOT / ".tmp"
LOCAL_TMP.mkdir(parents=True, exist_ok=True)
os.environ.setdefault("TMP", str(LOCAL_TMP))
os.environ.setdefault("TEMP", str(LOCAL_TMP))
os.environ.setdefault("TMPDIR", str(LOCAL_TMP))

app = FastAPI(title="km-ingest-service", version="0.1.0")


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "ok"}


@app.post("/ingest")
async def ingest(
    file: UploadFile = File(...),
    doc_id: str | None = Form(default=None),
    skip_mineru: bool = Form(default=False),
) -> dict[str, object]:
    config = IngestConfig.from_env()
    upload_dir = config.work_dir / "_uploads" / uuid.uuid4().hex
    upload_dir.mkdir(parents=True, exist_ok=True)

    filename = Path(file.filename or "source.bin").name
    upload_path = upload_dir / filename
    try:
        with upload_path.open("wb") as target:
            while chunk := await file.read(1024 * 1024):
                target.write(chunk)

        manifest = ingest_document(
            file_path=upload_path,
            config=config,
            doc_id=doc_id,
            skip_mineru=skip_mineru,
        )
        return manifest.to_dict()
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc
    except FileNotFoundError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc
    except Exception as exc:
        raise HTTPException(status_code=500, detail=str(exc)) from exc
    finally:
        await file.close()
        shutil.rmtree(upload_dir, ignore_errors=True)
