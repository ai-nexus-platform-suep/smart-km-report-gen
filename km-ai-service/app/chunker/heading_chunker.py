"""Heading-based chunker: splits document by markdown-style headings."""
import re
from typing import List
from .base import BaseChunker, ChunkResult

class HeadingChunker(BaseChunker):
    def __init__(self, max_section_length: int = 2048):
        self.max_section_length = max_section_length

    def chunk(self, text: str) -> List[ChunkResult]:
        lines = text.split("\n")
        chunks = []
        current_lines = []
        current_path = []
        heading_stack = [""] * 6

        def flush():
            if not current_lines:
                return
            content = "\n".join(current_lines).strip()
            if content:
                path = " > ".join(p for p in current_path if p)
                chunks.append(ChunkResult(content=content, chapter_path=path, chunk_type="section"))
            current_lines.clear()

        for line in lines:
            heading_match = re.match(r'^(#{1,6})\s+(.+)$', line.strip())
            if heading_match:
                flush()
                level = len(heading_match.group(1)) - 1
                title = heading_match.group(2).strip()
                heading_stack[level] = title
                for i in range(level + 1, 6):
                    heading_stack[i] = ""
                current_path = [h for h in heading_stack if h]
                current_lines.append(line)
            else:
                current_lines.append(line)
        flush()
        if not chunks:
            chunks.append(ChunkResult(content=text.strip(), chunk_type="paragraph"))
        return chunks
