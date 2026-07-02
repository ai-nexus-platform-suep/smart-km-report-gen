from __future__ import annotations

from dataclasses import dataclass
import hashlib
import re
import uuid


HEADING_PATTERN = re.compile(r"^(#{1,6})\s+(.+?)\s*$")
STRUCTURE_PATTERN = re.compile(
    r"^(第[一二三四五六七八九十百千万零〇两0-9]+[章节条款项].*|\d+(?:\.\d+){0,5}[\.、\s].*)$"
)
SENTENCE_PATTERN = re.compile(r"([^。！？!?；;\.]+[。！？!?；;\.]?)")
MAX_CHAPTER_PATH_LENGTH = 512


@dataclass(frozen=True)
class Chunk:
    id: str
    content: str
    chapter_path: str
    chunk_index: int
    chunk_type: str
    vector_id: str


@dataclass(frozen=True)
class ChunkOptions:
    strategy_type: str = "heading"
    chunk_size: int = 1200
    overlap: int = 120


def chunk_markdown(
    markdown: str,
    *,
    document_id: str,
    strategy: str | None = None,
    chunk_size: int | None = None,
    overlap: int | None = None,
) -> list[Chunk]:
    options = ChunkOptions(
        strategy_type=(strategy or "heading").lower(),
        chunk_size=max(200, int(chunk_size or 1200)),
        overlap=max(0, int(overlap or 120)),
    )
    text = _normalize(markdown)
    if not text:
        raise ValueError("Parsed Markdown is empty; cannot generate chunks")
    if options.strategy_type == "fixed_size":
        pieces = [(piece, "", "fixed_size") for piece in _fixed_split(text, options.chunk_size, options.overlap)]
    else:
        pieces = _structure_pieces(text, options.chunk_size)
    if not pieces:
        raise ValueError("Parsed Markdown produced no chunks")
    chunks: list[Chunk] = []
    for index, (content, chapter_path, chunk_type) in enumerate(pieces):
        chunk_id = _stable_chunk_id(document_id, index, content)
        chunks.append(
            Chunk(
                id=chunk_id,
                content=content,
                chapter_path=chapter_path[:MAX_CHAPTER_PATH_LENGTH],
                chunk_index=index,
                chunk_type=chunk_type,
                vector_id=chunk_id,
            )
        )
    return chunks


def _structure_pieces(markdown: str, chunk_size: int) -> list[tuple[str, str, str]]:
    heading_stack: list[tuple[int, str]] = []
    raw_pieces: list[tuple[str, str, str]] = []
    buffer: list[str] = []
    buffer_type = "paragraph"
    buffer_path = ""

    def flush() -> None:
        nonlocal buffer, buffer_type, buffer_path
        content = "\n".join(buffer).strip()
        if content:
            for piece in _split_oversized(content, chunk_size):
                raw_pieces.append((piece, buffer_path, buffer_type))
        buffer = []

    for block in _paragraphs(markdown):
        heading = HEADING_PATTERN.match(block)
        if heading:
            flush()
            level = len(heading.group(1))
            title = heading.group(2).strip()
            heading_stack = [(lvl, text) for lvl, text in heading_stack if lvl < level]
            heading_stack.append((level, title))
            buffer_path = _chapter_path(heading_stack)
            raw_pieces.append((title, buffer_path, "heading"))
            continue
        chapter_path = _chapter_path(heading_stack)
        chunk_type = _classify_block(block)
        if chunk_type in {"article", "clause"}:
            flush()
            buffer_path = chapter_path
            buffer_type = chunk_type
            buffer = [block]
            continue
        if buffer and (buffer_type != "paragraph" or len("\n".join(buffer)) + len(block) + 1 > chunk_size):
            flush()
        buffer_path = chapter_path
        buffer_type = "paragraph"
        buffer.append(block)
    flush()
    return _merge_small(raw_pieces, chunk_size)


def _paragraphs(markdown: str) -> list[str]:
    blocks = re.split(r"\n\s*\n", markdown)
    return [re.sub(r"\n+", "\n", block).strip() for block in blocks if block.strip()]


def _classify_block(block: str) -> str:
    first_line = block.splitlines()[0].strip()
    if STRUCTURE_PATTERN.match(first_line):
        if "章" in first_line or "节" in first_line or "条" in first_line or re.match(r"^\d+(?:\.\d+){1,5}", first_line):
            return "clause"
        return "article"
    return "paragraph"


def _split_oversized(content: str, chunk_size: int) -> list[str]:
    if len(content) <= chunk_size:
        return [content]
    sentences = [match.group(1).strip() for match in SENTENCE_PATTERN.finditer(content) if match.group(1).strip()]
    if not sentences:
        sentences = [content]
    pieces: list[str] = []
    current = ""
    for sentence in sentences:
        if len(sentence) > chunk_size:
            if current:
                pieces.append(current.strip())
                current = ""
            pieces.extend(_hard_split(sentence, chunk_size))
        elif current and len(current) + len(sentence) + 1 > chunk_size:
            pieces.append(current.strip())
            current = sentence
        else:
            current = (current + " " + sentence).strip() if current else sentence
    if current:
        pieces.append(current.strip())
    return pieces


def _merge_small(pieces: list[tuple[str, str, str]], chunk_size: int) -> list[tuple[str, str, str]]:
    merged: list[tuple[str, str, str]] = []
    for content, chapter_path, chunk_type in pieces:
        if not content.strip():
            continue
        if (
            merged
            and chunk_type != "heading"
            and merged[-1][1] == chapter_path
            and merged[-1][2] == chunk_type
            and len(merged[-1][0]) + len(content) + 1 <= chunk_size
        ):
            previous, _, _ = merged[-1]
            merged[-1] = (previous + "\n" + content, chapter_path, chunk_type)
        else:
            merged.append((content, chapter_path, chunk_type))
    return merged


def _fixed_split(text: str, chunk_size: int, overlap: int) -> list[str]:
    pieces: list[str] = []
    start = 0
    step = max(1, chunk_size - min(overlap, chunk_size - 1))
    while start < len(text):
        piece = text[start : start + chunk_size].strip()
        if piece:
            pieces.append(piece)
        start += step
    return pieces


def _hard_split(text: str, chunk_size: int) -> list[str]:
    return [text[index : index + chunk_size].strip() for index in range(0, len(text), chunk_size) if text[index : index + chunk_size].strip()]


def _chapter_path(stack: list[tuple[int, str]]) -> str:
    return " > ".join(title for _, title in stack)


def _normalize(markdown: str) -> str:
    text = markdown.replace("\r\n", "\n").replace("\r", "\n")
    text = re.sub(r"![^\n]*\([^)]*\)", "", text)
    text = re.sub(r"[ \t]+", " ", text)
    text = re.sub(r"\n{3,}", "\n\n", text)
    return text.strip()


def _stable_chunk_id(document_id: str, index: int, content: str) -> str:
    digest = hashlib.sha1(f"{document_id}:{index}:{content}".encode("utf-8")).hexdigest()
    return str(uuid.UUID(digest[:32]))
