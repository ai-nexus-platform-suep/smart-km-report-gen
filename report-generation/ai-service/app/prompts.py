"""Load prompt templates from ai-prompts directory."""

from __future__ import annotations

import json
from pathlib import Path
from typing import Any

PROMPTS_DIR = Path(__file__).resolve().parent.parent.parent / "ai-prompts"


def _read(name: str) -> str:
    path = PROMPTS_DIR / name
    if not path.exists():
        raise FileNotFoundError(f"Prompt file not found: {path}")
    return path.read_text(encoding="utf-8")


def fill_template(template: str, variables: dict[str, Any]) -> str:
    result = template
    for key, value in variables.items():
        placeholder = "{" + key + "}"
        if isinstance(value, (dict, list)):
            text = json.dumps(value, ensure_ascii=False, indent=2)
        else:
            text = "" if value is None else str(value)
        result = result.replace(placeholder, text)
    return result


def build_outline_messages(payload: dict[str, Any]) -> list[dict[str, str]]:
    system = _read("outline-system.md")
    user_tpl = _read("outline-user-template.md")
    variables = {
        "reportType": payload.get("reportType", ""),
        "reportTypeLabel": payload.get("reportTypeLabel", ""),
        "subject": payload.get("subject", ""),
        "name": payload.get("name", ""),
        "specialty": payload.get("specialty", ""),
        "powerPlant": payload.get("powerPlant", ""),
        "reportYear": payload.get("reportYear", ""),
        "contextJson": payload.get("context") or {},
    }
    return [
        {"role": "system", "content": system},
        {"role": "user", "content": fill_template(user_tpl, variables)},
    ]


def build_section_messages(payload: dict[str, Any], regenerate: bool = False) -> list[dict[str, str]]:
    system = _read("section-system.md")
    if regenerate:
        regen = _read("regenerate-template.md")
        system += "\n\n" + regen.split("## System Prompt", 1)[-1].split("## User Prompt", 1)[0]

    user_tpl = _read("section-user-template.md")
    if regenerate and payload.get("existingContentMarkdown"):
        user_tpl += (
            "\n\n---\n\n## 原有正文（供参考，可大幅改写）\n\n"
            + payload.get("existingContentMarkdown", "")
            + "\n\n---\n\n## 用户补充意见\n\n"
            + payload.get("userHint", "")
            + "\n\n请根据用户意见重新撰写本章节正文。只输出 Markdown，不要输出标题行。"
        )

    variables = {
        "reportType": payload.get("reportType", ""),
        "reportTypeLabel": payload.get("reportTypeLabel", ""),
        "subject": payload.get("subject", ""),
        "powerPlant": payload.get("powerPlant", ""),
        "specialty": payload.get("specialty", ""),
        "reportYear": payload.get("reportYear", ""),
        "sectionNumber": payload.get("sectionNumber", ""),
        "sectionTitle": payload.get("sectionTitle", ""),
        "sectionLevel": payload.get("sectionLevel", 2),
        "promptHint": payload.get("promptHint", ""),
        "outlineContext": payload.get("outlineContext", ""),
        "allowTables": payload.get("allowTables", False),
        "tablePlansJson": payload.get("tablePlans") or [],
    }
    return [
        {"role": "system", "content": system},
        {"role": "user", "content": fill_template(user_tpl, variables)},
    ]
