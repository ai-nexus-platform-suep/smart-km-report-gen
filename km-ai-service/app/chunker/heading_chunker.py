"""Heading-based chunker: splits document by markdown-style and Chinese headings."""
import re
from typing import List, Optional
from .base import BaseChunker, ChunkResult

class HeadingChunker(BaseChunker):
    def __init__(self, max_section_length: int = 2048):
        self.max_section_length = max_section_length

    def chunk(self, text: str) -> List[ChunkResult]:
        lines = text.split("\n")
        # Split into paragraphs first for better detection
        paragraphs = self._split_paragraphs(text)
        if len(paragraphs) <= 1:
            # No paragraph splits, use fixed-size fallback
            return self._fixed_size_fallback(text)

        chunks = []
        current_lines = []
        current_path = []

        def flush():
            if not current_lines:
                return
            content = "\n\n".join(current_lines).strip()
            if content:
                path = " > ".join(p for p in current_path if p)
                chunks.append(ChunkResult(content=content, chapter_path=path, chunk_type="section"))
            current_lines.clear()

        for para in paragraphs:
            heading_info = self._detect_heading(para.strip())
            if heading_info:
                flush()
                level = heading_info["level"]
                title = heading_info["title"]
                # Update heading stack
                while len(current_path) >= level:
                    current_path.pop() if current_path else None
                current_path.append(title)
                current_lines.append(para)
            else:
                current_lines.append(para)
        flush()

        if len(chunks) <= 1:
            return self._fixed_size_fallback(text)

        # Post-process: merge tiny chunks
        merged = []
        for c in chunks:
            if merged and len(c.content) < 100 and len(c.content) < 100:
                merged[-1].content += "\n\n" + c.content
            else:
                merged.append(c)
        return merged if merged else chunks

    def _detect_heading(self, line: str) -> Optional[dict]:
        """Detect if a line is a heading. Returns {level, title} or None."""
        if not line:
            return None
        stripped = line.strip()

        # Markdown: # Title
        m = re.match(r'^(#{1,6})\s+(.+)$', stripped)
        if m:
            return {"level": len(m.group(1)), "title": m.group(2).strip()}

        # Chinese: 第X章 / 第X节 (Chapter X / Section X)
        m = re.match(r'^第([一二三四五六七八九十百千零\d]+)([章节篇部])[\s、：:]*', stripped)
        if m:
            return {"level": 1, "title": m.group(0).strip()}

        # Chinese: 一、/ 二、/ （一）/ （二）/ 1. / 2. / (1) / (2)
        m = re.match(r'^[(（]?[一二三四五六七八九十百\d]+[)）、.．\s]', stripped)
        if m:
            # Check length - heading titles are usually short
            if len(stripped) < 80:
                return {"level": 2, "title": stripped}

        # Numbered: 1. Title / 1.1 Title / 1.1.1 Title
        m = re.match(r'^(\d+(?:[.]\d+)*)[.．\s]+(.+)$', stripped)
        if m:
            depth = m.group(1).count(".") + 1
            title = m.group(2).strip()
            if len(title) < 80:
                return {"level": min(depth, 4), "title": title}

        # Short bold/caps line looking like a heading
        if len(stripped) < 60 and stripped.endswith((":", "：", "?")):
            return {"level": 3, "title": stripped}

        return None

    def _split_paragraphs(self, text: str) -> List[str]:
        """Split text into paragraphs on blank lines."""
        raw = re.split(r"\n\s*\n", text)
        return [p.strip() for p in raw if p.strip()]

    def _fixed_size_fallback(self, text: str) -> List[ChunkResult]:
        """Fall back to fixed-size chunking when heading detection fails."""
        from .fixed_size_chunker import FixedSizeChunker
        fallback = FixedSizeChunker(chunk_size=1024, overlap=128, separator="\n\n")
        return fallback.chunk(text)
