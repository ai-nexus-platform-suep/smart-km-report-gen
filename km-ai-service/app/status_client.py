from __future__ import annotations

import logging
from typing import Any

import requests
from requests import RequestException

from .settings import Settings


logger = logging.getLogger(__name__)


class StatusClient:
    def __init__(self, settings: Settings) -> None:
        self._url = settings.backend_status_url

    def update(
        self,
        *,
        document_id: str,
        status: str,
        callback_url: str | None = None,
        job_id: str | None = None,
        attempt: int | None = None,
        error_msg: str | None = None,
        chunk_count: int | None = None,
    ) -> None:
        payload: dict[str, Any] = {
            "documentId": document_id,
            "status": status,
            "jobId": job_id,
            "attempt": attempt,
            "errorMsg": error_msg,
            "chunkCount": chunk_count,
        }
        url = callback_url or self._url
        try:
            response = requests.post(url, json=payload, timeout=10)
        except RequestException as exc:
            raise RuntimeError(
                f"Document status callback failed, url={url}, docId={document_id}, status={status}"
            ) from exc
        response.raise_for_status()
        logger.info("Document status callback sent, docId=%s, status=%s", document_id, status)
