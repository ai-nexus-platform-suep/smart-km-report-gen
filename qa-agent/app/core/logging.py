"""统一日志配置"""

import logging
import sys
import time
from pathlib import Path


def setup_logging(name: str = "qa-agent") -> logging.Logger:
    logger = logging.getLogger(name)
    if logger.handlers:
        return logger

    logger.setLevel(logging.DEBUG)

    # 控制台：INFO 及以上
    console = logging.StreamHandler(sys.stdout)
    console.setLevel(logging.INFO)
    console.setFormatter(logging.Formatter(
        fmt="%(asctime)s.%(msecs)03d %(levelname)-7s [%(name)s] %(message)s",
        datefmt="%H:%M:%S",
    ))
    logger.addHandler(console)

    # 文件：DEBUG 及以上
    log_dir = Path(__file__).resolve().parent.parent.parent / "logs"
    log_dir.mkdir(exist_ok=True)
    file_handler = logging.handlers.TimedRotatingFileHandler(
        log_dir / "qa-agent.log",
        when="D",
        interval=1,
        backupCount=7,
        encoding="utf-8",
    )
    file_handler.setLevel(logging.DEBUG)
    file_handler.setFormatter(logging.Formatter(
        fmt="%(asctime)s %(levelname)-7s [%(name)s] %(message)s",
        datefmt="%Y-%m-%d %H:%M:%S",
    ))
    logger.addHandler(file_handler)

    return logger


def get_logger(name: str) -> logging.Logger:
    return logging.getLogger(f"qa-agent.{name}")


class RequestTimer:
    """请求耗时记录"""

    def __init__(self, logger: logging.Logger, label: str):
        self.logger = logger
        self.label = label
        self.start: float = 0

    def __enter__(self):
        self.start = time.perf_counter()
        return self

    def __exit__(self, *args):
        elapsed = (time.perf_counter() - self.start) * 1000
        self.logger.info("%s 耗时=%.0fms", self.label, elapsed)
