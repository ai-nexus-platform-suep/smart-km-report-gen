from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path
import zipfile
import logging
import time
from typing import Any

import requests
from requests import RequestException

from .settings import Settings


logger = logging.getLogger(__name__)

NON_TERMINAL_STATES = {"waiting-file", "uploading", "pending", "running", "converting"}
ERROR_HINTS = {
    -30001: "file exceeds the 10 MB MinerU Agent lightweight API limit",
    -30002: "unsupported file type; use PDF, image, DOCX, PPTX, or XLSX direct files",
    -30003: "PDF exceeds the 20 page MinerU Agent lightweight API limit",
    -30004: "invalid MinerU Agent request parameters",
    -10002: "invalid or expired MinerU Agent task_id",
    -60001: "failed to generate MinerU paid upload URL; retry later",
    -60002: "failed to detect file type; check the file name extension and file format",
    -60003: "MinerU paid API could not read the file; check whether it is damaged",
    -60004: "empty file is not supported by MinerU paid API",
    -60005: "file exceeds the 200 MB MinerU paid API limit",
    "A0202": "invalid MinerU paid API token or missing Bearer prefix",
    "A0211": "expired MinerU paid API token",
}


@dataclass(frozen=True)
class MineruAgentParseResult:
    task_id: str | None
    markdown_path: Path
    state: str
    backend: str = "mineru-agent-file"
    batch_id: str | None = None
    data_id: str | None = None
    file_name: str | None = None
    api_base_url: str | None = None


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

    def parse_file(
        self,
        *,
        source_path: Path,
        file_name: str,
        output_dir: Path,
        data_id: str | None = None,
    ) -> MineruAgentParseResult:
        output_dir.mkdir(parents=True, exist_ok=True)
        deadline = time.monotonic() + self._timeout_seconds
        task_id, upload_url = self._submit_parse_file(file_name=file_name, deadline=deadline)
        self._upload_source_file(upload_url=upload_url, source_path=source_path, deadline=deadline)
        markdown_url = self._wait_for_markdown_url(task_id=task_id, deadline=deadline)
        markdown_path = output_dir / "content.md"
        self._download_markdown(markdown_url=markdown_url, target=markdown_path, deadline=deadline)
        return MineruAgentParseResult(
            task_id=task_id,
            markdown_path=markdown_path,
            state="done",
            backend="mineru-agent-file",
            data_id=data_id,
            file_name=file_name,
            api_base_url=self._base_url,
        )

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


class MineruPaidClient:
    def __init__(self, settings: Settings) -> None:
        self._base_url = settings.mineru_paid_api_base_url.rstrip("/")
        self._token = settings.mineru_api_token
        self._timeout_seconds = settings.mineru_timeout_seconds
        self._poll_interval_seconds = settings.mineru_poll_interval_seconds
        self._language = settings.mineru_lang
        self._method = settings.mineru_method
        self._enable_table = settings.mineru_enable_table
        self._enable_formula = settings.mineru_enable_formula
        self._model_version = settings.mineru_model_version
        self._page_ranges = settings.mineru_page_ranges

    @property
    def parse_options(self) -> dict[str, object]:
        options: dict[str, object] = {
            "backend": "paid_v4",
            "language": self._language,
            "model_version": self._model_version,
            "enable_table": self._enable_table,
            "enable_formula": self._enable_formula,
            "is_ocr": self._method == "ocr",
        }
        if self._page_ranges:
            options["page_ranges"] = self._page_ranges
        return options

    def parse_file(
        self,
        *,
        source_path: Path,
        file_name: str,
        output_dir: Path,
        data_id: str | None = None,
    ) -> MineruAgentParseResult:
        if not self._token:
            raise RuntimeError("MINERU_API_TOKEN is required when MINERU_BACKEND=paid_v4")

        output_dir.mkdir(parents=True, exist_ok=True)
        deadline = time.monotonic() + self._timeout_seconds
        batch_id, upload_url = self._request_upload_url(file_name=file_name, data_id=data_id, deadline=deadline)
        self._upload_source_file(upload_url=upload_url, source_path=source_path, batch_id=batch_id, deadline=deadline)
        result = self._wait_for_result(batch_id=batch_id, file_name=file_name, data_id=data_id, deadline=deadline)
        markdown_path = output_dir / "content.md"
        self._download_markdown_zip(full_zip_url=result["full_zip_url"], target=markdown_path, deadline=deadline)
        return MineruAgentParseResult(
            task_id=None,
            markdown_path=markdown_path,
            state="done",
            backend="mineru-paid-v4-file",
            batch_id=batch_id,
            data_id=data_id,
            file_name=str(result.get("file_name") or file_name),
            api_base_url=self._base_url,
        )

    def _request_upload_url(self, *, file_name: str, data_id: str | None, deadline: float) -> tuple[str, str]:
        file_payload: dict[str, object] = {
            "name": file_name,
            "is_ocr": self._method == "ocr",
        }
        if data_id:
            file_payload["data_id"] = data_id
        if self._page_ranges:
            file_payload["page_ranges"] = self._page_ranges

        payload = {
            "files": [file_payload],
            "model_version": self._model_version,
            "language": self._language,
            "enable_table": self._enable_table,
            "enable_formula": self._enable_formula,
        }
        logger.info("Requesting MinerU paid upload URL, fileName=%s, dataId=%s", file_name, data_id)
        response_payload = self._request_json("post", f"{self._base_url}/file-urls/batch", deadline=deadline, json=payload)
        self._raise_for_paid_api_code(response_payload, "MinerU paid upload URL request failed")
        data = response_payload.get("data") if isinstance(response_payload, dict) else None
        batch_id = data.get("batch_id") if isinstance(data, dict) else None
        upload_urls = data.get("file_urls") if isinstance(data, dict) else None
        upload_url = upload_urls[0] if isinstance(upload_urls, list) and upload_urls else None
        if not isinstance(batch_id, str) or not batch_id.strip():
            raise RuntimeError("MinerU paid upload URL response missing batch_id")
        if not isinstance(upload_url, str) or not upload_url.strip():
            raise RuntimeError(f"MinerU paid upload URL response missing file_urls, batchId={batch_id}")
        logger.info("MinerU paid upload URL created, batchId=%s, fileName=%s", batch_id, file_name)
        return batch_id, upload_url

    def _upload_source_file(self, *, upload_url: str, source_path: Path, batch_id: str, deadline: float) -> None:
        self._raise_if_expired(deadline, "MinerU paid source upload timed out")
        try:
            with source_path.open("rb") as source_file:
                response = requests.put(upload_url, data=source_file, timeout=self._remaining_timeout(deadline))
            if response.status_code == 429:
                raise RuntimeError("MinerU paid upload rate limit exceeded with HTTP 429; reduce parse concurrency or retry later")
            response.raise_for_status()
        except RuntimeError:
            raise
        except RequestException as exc:
            detail = _response_detail(getattr(exc, "response", None))
            raise RuntimeError(f"MinerU paid source upload failed, batchId={batch_id}, detail={detail}") from exc

    def _wait_for_result(
        self,
        *,
        batch_id: str,
        file_name: str,
        data_id: str | None,
        deadline: float,
    ) -> dict[str, Any]:
        while True:
            self._raise_if_expired(deadline, f"MinerU paid task timed out, batchId={batch_id}")
            response_payload = self._request_json(
                "get",
                f"{self._base_url}/extract-results/batch/{batch_id}",
                deadline=deadline,
            )
            self._raise_for_paid_api_code(response_payload, f"MinerU paid task status failed, batchId={batch_id}")
            data = response_payload.get("data") if isinstance(response_payload, dict) else None
            results = data.get("extract_result") if isinstance(data, dict) else None
            if isinstance(results, dict):
                results = [results]
            if not isinstance(results, list) or not results:
                raise RuntimeError(f"MinerU paid task response missing extract_result, batchId={batch_id}")

            result = self._select_result(results, file_name=file_name, data_id=data_id)
            state = result.get("state")
            if state == "done":
                full_zip_url = result.get("full_zip_url")
                if not isinstance(full_zip_url, str) or not full_zip_url.strip():
                    raise RuntimeError(f"MinerU paid task missing full_zip_url, batchId={batch_id}")
                logger.info("MinerU paid task completed, batchId=%s, fileName=%s", batch_id, file_name)
                return result

            if state == "failed":
                err_msg = result.get("err_msg") or response_payload.get("msg") or "unknown error"
                raise RuntimeError(f"MinerU paid task failed, batchId={batch_id}, errMsg={err_msg}")

            if state not in NON_TERMINAL_STATES:
                raise RuntimeError(f"MinerU paid task returned unknown state, batchId={batch_id}, state={state}")

            logger.info("MinerU paid task pending, batchId=%s, state=%s", batch_id, state)
            sleep_seconds = min(self._poll_interval_seconds, max(0.0, deadline - time.monotonic()))
            if sleep_seconds > 0:
                time.sleep(sleep_seconds)

    def _select_result(self, results: list[object], *, file_name: str, data_id: str | None) -> dict[str, Any]:
        dict_results = [item for item in results if isinstance(item, dict)]
        if not dict_results:
            raise RuntimeError("MinerU paid task response extract_result contains no result objects")
        if data_id:
            for item in dict_results:
                if item.get("data_id") == data_id:
                    return item
        for item in dict_results:
            if item.get("file_name") == file_name:
                return item
        return dict_results[0]

    def _download_markdown_zip(self, *, full_zip_url: str, target: Path, deadline: float) -> None:
        self._raise_if_expired(deadline, "MinerU paid result zip download timed out")
        target.parent.mkdir(parents=True, exist_ok=True)
        zip_path = target.parent / "mineru-result.zip"
        try:
            with requests.get(full_zip_url, stream=True, timeout=self._remaining_timeout(deadline)) as response:
                response.raise_for_status()
                with zip_path.open("wb") as output_file:
                    for chunk in response.iter_content(chunk_size=1024 * 1024):
                        if chunk:
                            output_file.write(chunk)
        except RequestException as exc:
            raise RuntimeError("MinerU paid result zip download failed") from exc

        try:
            with zipfile.ZipFile(zip_path) as archive:
                markdown_members = [name for name in archive.namelist() if Path(name).name == "full.md"]
                if not markdown_members:
                    raise RuntimeError("MinerU paid result zip missing full.md")
                with archive.open(sorted(markdown_members)[0]) as markdown_file:
                    target.write_bytes(markdown_file.read())
        except zipfile.BadZipFile as exc:
            raise RuntimeError("MinerU paid result zip is invalid") from exc
        finally:
            try:
                zip_path.unlink()
            except FileNotFoundError:
                pass

    def _request_json(self, method: str, url: str, *, deadline: float, **kwargs: Any) -> dict[str, Any]:
        self._raise_if_expired(deadline, "MinerU paid API request timed out")
        headers = kwargs.pop("headers", {})
        headers["Authorization"] = f"Bearer {self._token}"
        headers.setdefault("Content-Type", "application/json")
        try:
            if method == "post":
                response = requests.post(url, headers=headers, timeout=self._remaining_timeout(deadline), **kwargs)
            else:
                response = requests.get(url, headers=headers, timeout=self._remaining_timeout(deadline), **kwargs)
            if response.status_code == 429:
                raise RuntimeError("MinerU paid API rate limit exceeded with HTTP 429; reduce parse concurrency or retry later")
            response.raise_for_status()
        except RuntimeError:
            raise
        except RequestException as exc:
            detail = _response_detail(getattr(exc, "response", None))
            raise RuntimeError(f"MinerU paid API HTTP request failed, url={url}, detail={detail}") from exc

        try:
            payload = response.json()
        except ValueError as exc:
            raise RuntimeError(f"MinerU paid API returned non-JSON response, url={url}") from exc
        if not isinstance(payload, dict):
            raise RuntimeError(f"MinerU paid API returned invalid JSON payload, url={url}")
        return payload

    def _remaining_timeout(self, deadline: float) -> float:
        return max(1.0, deadline - time.monotonic())

    def _raise_if_expired(self, deadline: float, message: str) -> None:
        if time.monotonic() >= deadline:
            raise RuntimeError(message)

    @staticmethod
    def _raise_for_paid_api_code(payload: dict[str, Any], message: str) -> None:
        code = payload.get("code")
        if code == 0 or code == "0":
            return
        msg = payload.get("msg") or "unknown error"
        trace_id = payload.get("trace_id") or ""
        trace_suffix = f", traceId={trace_id}" if trace_id else ""
        raise RuntimeError(f"{message}: code={code}, msg={msg}{trace_suffix}{_error_hint(code)}")


def _error_hint(code: object) -> str:
    hint = ERROR_HINTS.get(code) or ERROR_HINTS.get(_normalize_error_code(code))
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
    text = str(_redact_payload(payload))
    if len(text) > 1000:
        return f"{text[:1000]}..."
    return text


def _redact_payload(payload: object) -> object:
    if isinstance(payload, dict):
        return {
            key: "<redacted>" if _is_sensitive_payload_key(key) else _redact_payload(value)
            for key, value in payload.items()
        }
    if isinstance(payload, list):
        return [_redact_payload(item) for item in payload]
    return payload


def _is_sensitive_payload_key(key: object) -> bool:
    normalized = str(key).lower().replace("-", "_")
    return normalized in {
        "file_url",
        "file_urls",
        "markdown_url",
        "full_zip_url",
        "authorization",
        "token",
        "api_token",
        "mineru_api_token",
    }
