from __future__ import annotations

from pathlib import Path
import re
import shutil
import uuid

from .artifacts import collect_artifacts
from .config import IngestConfig
from .manifest import IngestManifest
from .minio_store import MinioStore
from .mineru_runner import run_mineru


DOC_ID_PATTERN = re.compile(r"^[A-Za-z0-9][A-Za-z0-9._-]{0,127}$")


def ingest_document(
    file_path: Path,
    config: IngestConfig,
    doc_id: str | None = None,
    skip_mineru: bool = False,
) -> IngestManifest:
    source = file_path.resolve()
    if not source.is_file():
        raise FileNotFoundError(f"Input file not found: {source}")

    actual_doc_id = _resolve_doc_id(doc_id)
    doc_work_dir = config.work_dir / actual_doc_id
    input_dir = doc_work_dir / "input"
    mineru_output_dir = doc_work_dir / "mineru-output"
    normalized_dir = doc_work_dir / "normalized"
    if doc_work_dir.exists():
        shutil.rmtree(doc_work_dir)
    input_dir.mkdir(parents=True, exist_ok=True)

    local_source = input_dir / f"source{source.suffix.lower()}"
    shutil.copy2(source, local_source)

    store = MinioStore(config)
    raw_object = f"raw/{actual_doc_id}/source{source.suffix.lower()}"
    store.upload_file(raw_object, local_source)

    if skip_mineru:
        return IngestManifest(
            doc_id=actual_doc_id,
            raw_object=raw_object,
            markdown_object=None,
            parser_backend=config.mineru_backend,
        )

    try:
        run_mineru(local_source, mineru_output_dir, config)
        artifacts = collect_artifacts(mineru_output_dir, normalized_dir)
        markdown_object = _upload_optional(store, artifacts.markdown, f"parsed/{actual_doc_id}/content.md")
        json_objects: dict[str, str] = {}
        middle_object = _upload_optional(store, artifacts.middle_json, f"parsed/{actual_doc_id}/middle.json")
        if middle_object:
            json_objects["middle"] = middle_object
        layout_object = _upload_optional(store, artifacts.layout_json, f"parsed/{actual_doc_id}/layout.json")
        if layout_object:
            json_objects["layout"] = layout_object
        image_objects = [
            store.upload_file(f"parsed/{actual_doc_id}/images/{image.name}", image)
            for image in artifacts.images
        ]
        return IngestManifest(
            doc_id=actual_doc_id,
            raw_object=raw_object,
            markdown_object=markdown_object,
            json_objects=json_objects,
            image_objects=image_objects,
            parser_backend=config.mineru_backend,
        )
    except Exception as exc:
        return IngestManifest(
            doc_id=actual_doc_id,
            raw_object=raw_object,
            markdown_object=None,
            parser_backend=config.mineru_backend,
            error=str(exc),
        )


def _upload_optional(store: MinioStore, path: Path | None, object_name: str) -> str | None:
    if path is None:
        return None
    return store.upload_file(object_name, path)


def _resolve_doc_id(doc_id: str | None) -> str:
    if doc_id is None or not doc_id.strip():
        return uuid.uuid4().hex
    actual_doc_id = doc_id.strip()
    if not DOC_ID_PATTERN.fullmatch(actual_doc_id):
        raise ValueError("doc_id must be 1-128 characters using letters, numbers, dot, underscore, or hyphen; it must start with a letter or number")
    return actual_doc_id
