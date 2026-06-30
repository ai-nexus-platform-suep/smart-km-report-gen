from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path
import shutil


IMAGE_EXTENSIONS = {".png", ".jpg", ".jpeg", ".webp"}


@dataclass(frozen=True)
class ParsedArtifacts:
    markdown: Path | None
    middle_json: Path | None
    layout_json: Path | None
    images: list[Path]


def collect_artifacts(output_dir: Path, normalized_dir: Path) -> ParsedArtifacts:
    markdown = _first_file(output_dir, {".md"})
    json_files = list(output_dir.rglob("*.json"))
    middle_json = _find_named(json_files, "middle")
    layout_json = _find_named(json_files, "layout")
    images = sorted(path for path in output_dir.rglob("*") if path.is_file() and path.suffix.lower() in IMAGE_EXTENSIONS)

    normalized_dir.mkdir(parents=True, exist_ok=True)
    normalized_images_dir = normalized_dir / "images"

    normalized_markdown = _copy_if_present(markdown, normalized_dir / "content.md")
    normalized_middle = _copy_if_present(middle_json, normalized_dir / "middle.json")
    normalized_layout = _copy_if_present(layout_json, normalized_dir / "layout.json")

    normalized_images: list[Path] = []
    for index, image in enumerate(images, start=1):
        normalized_images_dir.mkdir(parents=True, exist_ok=True)
        target = normalized_images_dir / f"page_{index}{image.suffix.lower()}"
        shutil.copy2(image, target)
        normalized_images.append(target)

    return ParsedArtifacts(
        markdown=normalized_markdown,
        middle_json=normalized_middle,
        layout_json=normalized_layout,
        images=normalized_images,
    )


def _first_file(root: Path, extensions: set[str]) -> Path | None:
    matches = sorted(path for path in root.rglob("*") if path.is_file() and path.suffix.lower() in extensions)
    return matches[0] if matches else None


def _find_named(paths: list[Path], keyword: str) -> Path | None:
    keyword = keyword.lower()
    exact = [path for path in paths if path.stem.lower() == keyword]
    if exact:
        return sorted(exact)[0]
    contains = [path for path in paths if keyword in path.stem.lower()]
    return sorted(contains)[0] if contains else None


def _copy_if_present(source: Path | None, target: Path) -> Path | None:
    if source is None:
        return None
    target.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(source, target)
    return target
