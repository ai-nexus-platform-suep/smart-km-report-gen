from __future__ import annotations

from pathlib import Path
import os
import subprocess

from .config import IngestConfig


def run_mineru(input_path: Path, output_dir: Path, config: IngestConfig) -> None:
    output_dir.mkdir(parents=True, exist_ok=True)
    env = os.environ.copy()
    service_root = Path(__file__).resolve().parents[1]
    env.setdefault("PIP_CACHE_DIR", str(service_root / ".cache" / "pip"))
    env.setdefault("HF_HOME", str(service_root / ".cache" / "huggingface"))
    env.setdefault("HUGGINGFACE_HUB_CACHE", str(service_root / ".cache" / "huggingface" / "hub"))
    env.setdefault("MODELSCOPE_CACHE", str(service_root / ".cache" / "modelscope"))
    env.setdefault("MINERU_MODEL_SOURCE", "modelscope")
    local_config = service_root / ".mineru" / "mineru.json"
    if local_config.exists():
        env.setdefault("MINERU_TOOLS_CONFIG_JSON", str(local_config))

    command = [
        config.mineru_command,
        "-p",
        str(input_path),
        "-o",
        str(output_dir),
        "-b",
        config.mineru_backend,
    ]
    try:
        completed = subprocess.run(command, env=env, check=False, capture_output=True, text=True)
    except FileNotFoundError as exc:
        raise RuntimeError("MinerU command not found. Install dependencies in km-ingest-service/.venv first.") from exc

    if completed.returncode != 0:
        detail = (completed.stderr or completed.stdout or "MinerU failed").strip()
        raise RuntimeError(detail)
