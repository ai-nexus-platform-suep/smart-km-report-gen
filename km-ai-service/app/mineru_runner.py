from __future__ import annotations

from pathlib import Path
import logging
import os
import subprocess
import time

from .settings import SERVICE_ROOT, Settings


logger = logging.getLogger(__name__)


def run_mineru(input_path: Path, output_dir: Path, settings: Settings) -> None:
    output_dir.mkdir(parents=True, exist_ok=True)
    log_path = output_dir / "mineru.log"
    env = os.environ.copy()
    env.setdefault("PIP_CACHE_DIR", str(SERVICE_ROOT / ".cache" / "pip"))
    env.setdefault("HF_HOME", str(SERVICE_ROOT / ".cache" / "huggingface"))
    env.setdefault("HUGGINGFACE_HUB_CACHE", str(SERVICE_ROOT / ".cache" / "huggingface" / "hub"))
    env.setdefault("MODELSCOPE_CACHE", str(SERVICE_ROOT / ".cache" / "modelscope"))
    env.setdefault("MINERU_MODEL_SOURCE", "modelscope")
    local_config = SERVICE_ROOT / ".mineru" / "mineru.json"
    if local_config.exists():
        env.setdefault("MINERU_TOOLS_CONFIG_JSON", str(local_config))

    command = [
        settings.mineru_command,
        "-p",
        str(input_path),
        "-o",
        str(output_dir),
        "-b",
        settings.mineru_backend,
        "--formula",
        _bool_arg(settings.mineru_enable_formula),
        "--table",
        _bool_arg(settings.mineru_enable_table),
        "--image-analysis",
        _bool_arg(settings.mineru_enable_image_analysis),
    ]
    logger.info(
        "MinerU started, input=%s, output=%s, backend=%s, timeout=%ss",
        input_path,
        output_dir,
        settings.mineru_backend,
        settings.mineru_timeout_seconds,
    )
    started_at = time.monotonic()
    with log_path.open("w", encoding="utf-8", errors="replace") as log_file:
        try:
            process = subprocess.Popen(
                command,
                env=env,
                stdout=log_file,
                stderr=subprocess.STDOUT,
                text=True,
            )
        except FileNotFoundError as exc:
            raise RuntimeError("MinerU command not found. Install km-ai-service dependencies first.") from exc

        try:
            return_code = process.wait(timeout=settings.mineru_timeout_seconds)
        except subprocess.TimeoutExpired as exc:
            _terminate_process_tree(process.pid)
            raise RuntimeError(f"MinerU timed out after {settings.mineru_timeout_seconds} seconds. Log: {log_path}") from exc

    duration = time.monotonic() - started_at
    logger.info("MinerU finished, input=%s, returnCode=%s, duration=%.1fs, log=%s", input_path, return_code, duration, log_path)

    if return_code != 0:
        detail = _tail_text(log_path, 2000) or "MinerU failed"
        raise RuntimeError(detail)


def _tail_text(path: Path, limit: int) -> str:
    if not path.exists():
        return ""
    text = path.read_text(encoding="utf-8", errors="replace").strip()
    if len(text) > limit:
        return text[-limit:]
    return text


def _bool_arg(value: bool) -> str:
    return "true" if value else "false"


def _terminate_process_tree(pid: int) -> None:
    if os.name == "nt":
        subprocess.run(["taskkill", "/F", "/T", "/PID", str(pid)], check=False, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        return
    try:
        os.kill(pid, 9)
    except OSError:
        pass
