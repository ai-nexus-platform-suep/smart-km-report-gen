"""Fixed-size chunker with configurable overlap."""
from typing import List
from .base import BaseChunker, ChunkResult

class FixedSizeChunker(BaseChunker):
    def __init__(self, chunk_size: int = 512, overlap: int = 50, separator: str = "\n\n"):
        self.chunk_size = max(128, min(2048, chunk_size))
        self.overlap = max(0, min(256, overlap))
        self.separator = separator

    def chunk(self, text: str) -> List[ChunkResult]:
        paragraphs = text.split(self.separator) if self.separator else [text]
        chunks = []
        current = []
        current_len = 0

        for para in paragraphs:
            para = para.strip()
            if not para:
                continue
            if current_len + len(para) > self.chunk_size and current:
                chunks.append(ChunkResult(content="\n\n".join(current), chunk_type="paragraph"))
                overlap_text = []
                overlap_len = 0
                for p in reversed(current):
                    if overlap_len + len(p) >= self.overlap:
                        break
                    overlap_text.insert(0, p)
                    overlap_len += len(p)
                current = overlap_text
                current_len = overlap_len
            current.append(para)
            current_len += len(para)
        if current:
            chunks.append(ChunkResult(content="\n\n".join(current), chunk_type="paragraph"))
        if not chunks:
            chunks.append(ChunkResult(content=text.strip(), chunk_type="paragraph"))
        return chunks
