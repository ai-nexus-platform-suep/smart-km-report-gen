from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path
import logging
import time
from typing import Any

import requests
from requests import RequestException

from .settings import Settings


logger = logging.getLogger(__name__)

NON_TERMINAL_STATES = {"waiting-file", "uploading", "pending", "running"}
ERROR_HINTS = {
    -30001: "file exceeds the 10 MB MinerU Agent lightweight API limit",
    -30002: "unsupported file type; use PDF, image, DOCX, PPTX, or XLSX direct files",
    -30003: "PDF exceeds the 20 page MinerU Agent lightweight API limit",
    -30004: "invalid MinerU Agent request parameters",
    -10002: "invalid or expired MinerU Agent task_id",
}


@dataclass(frozen=True)
class MineruAgentParseResult:
    task_id: str
    markdown_path: Path
    state: str


class MineruAgentClient:
    def __init__(self, settings: Settings) -> None:
        self._base_url = settings.mineru_agent_api_base_url.rstrip("/")
        self._timeout_seconds = settings.mineru_timeout_seconds
        self._poll_interval_seconds = settings.mineru_poll_interval_seconds
        self._language = settings.mineru_lang
        self._method = settings.mineru_method
        self._enable_table = settings.mineru_enable_table
        self._enable_formula = settings.mineru_enable_formula

    @property
    def parse_options(self) -> dict[str, object]:
        return {
            "language": self._language,
            "enable_table": self._enable_table,
            "enable_formula": self._enable_formula,
            "is_ocr": self._method == "ocr",
        }

    def parse_file(self, *, source_path: Path, file_name: str, output_dir: Path) -> MineruAgentParseResult:
        output_dir.mkdir(parents=True, exist_ok=True)
        deadline = time.monotonic() + self._timeout_seconds
        task_id, upload_url = self._submit_parse_file(file_name=file_name, deadline=deadline)
        self._upload_source_file(upload_url=upload_url, source_path=source_path, deadline=deadline)
        markdown_url = self._wait_for_markdown_url(task_id=task_id, deadline=deadline)
        markdown_path = output_dir / "content.md"
        self._download_markdown(markdown_url=markdown_url, target=markdown_path, deadline=deadline)
        return MineruAgentParseResult(task_id=task_id, markdown_path=markdown_path, state="done")

    def _submit_parse_file(self, *, file_name: str, deadline: float) -> tuple[str, str]:
        payload = {
            "file_name": file_name,
            "language": self._language,
            "enable_table": self._enable_table,
            "is_ocr": self._method == "ocr",
            "enable_formula": self._enable_formula,
        }
        logger.info("Submitting MinerU Agent file parse, fileName=%s", file_name)
        response_payload = self._request_json("post", f"{self._base_url}/parse/file", deadline=deadline, json=payload)
        self._raise_for_api_code(response_payload, "MinerU Agent file parse submit failed")
        data = response_payload.get("data") if isinstance(response_payload, dict) else None
        task_id = data.get("task_id") if isinstance(data, dict) else None
        upload_url = data.get("file_url") if isinstance(data, dict) else None
        if not isinstance(task_id, str) or not task_id.strip():
            raise RuntimeError(f"MinerU Agent submit response missing task_id: {_safe_payload(response_payload)}")
        if not isinstance(upload_url, str) or not upload_url.strip():
            raise RuntimeError(f"MinerU Agent submit response missing file_url, taskId={task_id}: {_safe_payload(response_payload)}")
        logger.info("MinerU Agent file parse submitted, taskId=%s, fileName=%s", task_id, file_name)
        return task_id, upload_url

    def _upload_source_file(self, *, upload_url: str, source_path: Path, deadline: float) -> None:
        self._raise_if_expired(deadline, "MinerU Agent source upload timed out")
        try:
            with source_path.open("rb") as source_file:
                response = requests.put(upload_url, data=source_file, timeout=self._remaining_timeout(deadline))
            if response.status_code == 429:
                raise RuntimeError("MinerU Agent upload rate limit exceeded with HTTP 429; reduce parse concurrency or retry later")
            response.raise_for_status()
        except RuntimeError:
            raise
        except RequestException as exc:
            detail = _response_detail(getattr(exc, "response", None))
            raise RuntimeError(f"MinerU Agent source upload failed, detail={detail}") from exc

    def _wait_for_markdown_url(self, *, task_id: str, deadline: float) -> str:
        while True:
            self._raise_if_expired(deadline, f"MinerU Agent task timed out, taskId={task_id}")
            response_payload = self._request_json("get", f"{self._base_url}/parse/{task_id}", deadline=deadline)
            self._raise_for_api_code(response_payload, f"MinerU Agent task status failed, taskId={task_id}")
            data = response_payload.get("data") if isinstance(response_payload, dict) else None
            if not isinstance(data, dict):
                raise RuntimeError(f"MinerU Agent task response missing data, taskId={task_id}: {_safe_payload(response_payload)}")

            state = data.get("state")
            if state == "done":
                markdown_url = data.get("markdown_url")
                if not isinstance(markdown_url, str) or not markdown_url.strip():
                    raise RuntimeError(f"MinerU Agent task missing markdown_url, taskId={task_id}: {_safe_payload(response_payload)}")
                logger.info("MinerU Agent task completed, taskId=%s", task_id)
                return markdown_url

            if state == "failed":
                err_code = data.get("err_code")
                err_msg = data.get("err_msg") or response_payload.get("msg") or "unknown error"
                hint = _error_hint(err_code)
                raise RuntimeError(f"MinerU Agent task failed, taskId={task_id}, errCode={err_code}, errMsg={err_msg}{hint}")

            if state not in NON_TERMINAL_STATES:
                raise RuntimeError(f"MinerU Agent task returned unknown state, taskId={task_id}, state={state}")

            logger.info("MinerU Agent task pending, taskId=%s, state=%s", task_id, state)
            sleep_seconds = min(self._poll_interval_seconds, max(0.0, deadline - time.monotonic()))
            if sleep_seconds > 0:
                time.sleep(sleep_seconds)

    def _download_markdown(self, *, markdown_url: str, target: Path, deadline: float) -> None:
        self._raise_if_expired(deadline, "MinerU Agent markdown download timed out")
        target.parent.mkdir(parents=True, exist_ok=True)
        try:
            response = requests.get(markdown_url, timeout=self._remaining_timeout(deadline))
            response.raise_for_status()
        except RequestException as exc:
            raise RuntimeError("MinerU Agent markdown download failed") from exc
        target.write_bytes(response.content)

    def _request_json(self, method: str, url: str, *, deadline: float, **kwargs: Any) -> dict[str, Any]:
        self._raise_if_expired(deadline, "MinerU Agent request timed out")
        try:
            if method == "post":
                response = requests.post(url, timeout=self._remaining_timeout(deadline), **kwargs)
            else:
                response = requests.get(url, timeout=self._remaining_timeout(deadline), **kwargs)
            if response.status_code == 429:
                raise RuntimeError("MinerU Agent rate limit exceeded with HTTP 429; reduce parse concurrency or retry later")
            response.raise_for_status()
        except RuntimeError:
            raise
        except RequestException as exc:
            detail = _response_detail(getattr(exc, "response", None))
            raise RuntimeError(f"MinerU Agent HTTP request failed, url={url}, detail={detail}") from exc

        try:
            payload = response.json()
        except ValueError as exc:
            raise RuntimeError(f"MinerU Agent returned non-JSON response, url={url}") from exc
        if not isinstance(payload, dict):
            raise RuntimeError(f"MinerU Agent returned invalid JSON payload, url={url}")
        return payload

    def _remaining_timeout(self, deadline: float) -> float:
        return max(1.0, deadline - time.monotonic())

    def _raise_if_expired(self, deadline: float, message: str) -> None:
        if time.monotonic() >= deadline:
            raise RuntimeError(message)

    @staticmethod
    def _raise_for_api_code(payload: dict[str, Any], message: str) -> None:
        code = payload.get("code")
        if code == 0:
            return
        raise RuntimeError(f"{message}: {_safe_payload(payload)}{_error_hint(code)}")


def _error_hint(code: object) -> str:
    hint = ERROR_HINTS.get(_normalize_error_code(code))
    return f" ({hint})" if hint else ""


def _normalize_error_code(code: object) -> int | None:
    if isinstance(code, int):
        return code
    if isinstance(code, str):
        try:
            return int(code)
        except ValueError:
            return None
    return None


def _response_detail(response: requests.Response | None) -> str:
    if response is None:
        return "no response"
    try:
        payload = response.json()
    except ValueError:
        return response.text.strip() or response.reason
    return _safe_payload(payload)


def _safe_payload(payload: object) -> str:
    text = str(payload)
    if len(text) > 1000:
        return f"{text[:1000]}..."
    return text
