from __future__ import annotations

from dataclasses import dataclass, field


@dataclass(frozen=True)
class IngestManifest:
    doc_id: str
    raw_object: str
    markdown_object: str | None
    json_objects: dict[str, str] = field(default_factory=dict)
    image_objects: list[str] = field(default_factory=list)
    parser_backend: str = "pipeline"
    error: str | None = None

    def to_dict(self) -> dict[str, object]:
        return {
            "doc_id": self.doc_id,
            "raw_object": self.raw_object,
            "markdown_object": self.markdown_object,
            "json_objects": self.json_objects,
            "image_objects": self.image_objects,
            "parser_backend": self.parser_backend,
            "error": self.error,
        }
