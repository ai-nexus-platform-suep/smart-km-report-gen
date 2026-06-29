"""Chunking strategies: paragraph, heading, fixed_size (sliding window), ngram."""
from .base import BaseChunker, ChunkResult
from .heading_chunker import HeadingChunker
from .fixed_size_chunker import FixedSizeChunker
from .paragraph_chunker import ParagraphChunker
from .ngram_chunker import NgramChunker


def create_chunker(strategy: dict) -> BaseChunker:
    t = strategy.get("type", "heading")
    chunk_size = strategy.get("chunk_size", 500)
    overlap = strategy.get("overlap", 50)

    if t == "heading":
        return HeadingChunker()
    elif t == "paragraph":
        return ParagraphChunker(max_chars=token_to_chars(chunk_size))
    elif t == "ngram":
        return NgramChunker(n_size=chunk_size, overlap=overlap)
    elif t == "fixed_size":
        return FixedSizeChunker(chunk_size=token_to_chars(chunk_size) // 4,
                                overlap=overlap, separator=strategy.get("separator", "\n\n"))
    return HeadingChunker()


def token_to_chars(tokens: int) -> int:
    """Approximate token-to-character conversion.
    1 token ~ 1.5 Chinese chars, ~0.75 English chars.
    Use conservative estimate: 2 chars per token for safety.
    """
    return tokens * 2


def chars_to_token(text: str) -> int:
    """Rough token count estimate."""
    return len(text) // 2
