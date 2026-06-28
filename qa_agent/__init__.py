from pathlib import Path

_LEGACY_PACKAGE_DIR = Path(__file__).resolve().parent.parent / "qa-agent"

if _LEGACY_PACKAGE_DIR.exists():
    __path__.append(str(_LEGACY_PACKAGE_DIR))
