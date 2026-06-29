from .base import BaseChunker, ChunkResult
from .heading_chunker import HeadingChunker
from .fixed_size_chunker import FixedSizeChunker

def create_chunker(strategy: dict) -> BaseChunker:
    t = strategy.get("type", "heading")
    if t == "heading":
        return HeadingChunker()
    elif t == "fixed_size":
        return FixedSizeChunker(
            chunk_size=strategy.get("chunk_size", 512),
            overlap=strategy.get("overlap", 50),
            separator=strategy.get("separator", "\n\n")
        )
    raise ValueError(f"Unknown chunk strategy: {t}")
