"""Base chunker interface."""
from abc import ABC, abstractmethod
from typing import List

class ChunkResult:
    def __init__(self, content: str, chapter_path: str = "", chunk_type: str = "paragraph"):
        self.content = content
        self.chapter_path = chapter_path
        self.chunk_type = chunk_type

class BaseChunker(ABC):
    @abstractmethod
    def chunk(self, text: str) -> List[ChunkResult]:
        pass
