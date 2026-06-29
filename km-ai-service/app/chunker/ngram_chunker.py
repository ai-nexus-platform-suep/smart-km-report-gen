"""N-gram chunker: splits text into overlapping character n-grams."""
from typing import List
from .base import BaseChunker, ChunkResult


class NgramChunker(BaseChunker):
    """Character-level N-gram slicing with configurable overlap."""

    def __init__(self, n_size: int = 500, overlap: int = 50):
        self.n_size = max(64, min(4096, n_size))
        self.overlap = max(0, min(self.n_size // 2, overlap))
        self.stride = self.n_size - self.overlap

    def chunk(self, text: str) -> List[ChunkResult]:
        if not text.strip():
            return [ChunkResult(content="", chunk_type="ngram")]
        if len(text) <= self.n_size:
            return [ChunkResult(content=text.strip(), chunk_type="ngram")]

        chunks = []
        start = 0
        index = 0
        while start < len(text):
            end = min(start + self.n_size, len(text))
            chunk_text = text[start:end]
            if chunk_text.strip():
                chunks.append(ChunkResult(
                    content=chunk_text.strip(),
                    chunk_type="ngram"
                ))
            start += self.stride
            index += 1
        return chunks
