"""Paragraph chunker: groups paragraphs up to max_chars limit."""
from typing import List
from .base import BaseChunker, ChunkResult


class ParagraphChunker(BaseChunker):
    """Merge consecutive paragraphs until they reach max_chars."""

    def __init__(self, max_chars: int = 1000):
        self.max_chars = max_chars

    def chunk(self, text: str) -> List[ChunkResult]:
        import re
        paragraphs = re.split(r"\n\s*\n", text)
        paragraphs = [p.strip() for p in paragraphs if p.strip()]
        if not paragraphs:
            return [ChunkResult(content=text.strip(), chunk_type="paragraph")]

        chunks = []
        current = []
        current_len = 0

        for p in paragraphs:
            p_len = len(p)
            if current_len + p_len > self.max_chars and current:
                chunks.append(ChunkResult(
                    content="\n\n".join(current),
                    chunk_type="paragraph"
                ))
                current = [p]
                current_len = p_len
            else:
                current.append(p)
                current_len += p_len

        if current:
            chunks.append(ChunkResult(
                content="\n\n".join(current),
                chunk_type="paragraph"
            ))
        return chunks
