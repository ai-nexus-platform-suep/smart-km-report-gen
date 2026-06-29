"""雪花 ID 生成器 (人员 B 独占)"""

import threading
import time

_EPOCH_MS = 1_609_459_200_000  # 2021-01-01 UTC


class SnowflakeGenerator:
    def __init__(self, worker_id: int = 1) -> None:
        self._worker_id = worker_id & 0x3FF
        self._sequence = 0
        self._last_timestamp = -1
        self._lock = threading.Lock()

    def next_id(self) -> int:
        with self._lock:
            timestamp = int(time.time() * 1000)
            if timestamp == self._last_timestamp:
                self._sequence = (self._sequence + 1) & 0xFFF
                if self._sequence == 0:
                    while timestamp <= self._last_timestamp:
                        timestamp = int(time.time() * 1000)
            else:
                self._sequence = 0
            self._last_timestamp = timestamp
            return ((timestamp - _EPOCH_MS) << 22) | (self._worker_id << 12) | self._sequence


id_generator = SnowflakeGenerator()
