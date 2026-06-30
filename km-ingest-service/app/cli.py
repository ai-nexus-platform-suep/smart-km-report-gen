from __future__ import annotations

import argparse
import json
from pathlib import Path
import sys

from dotenv import load_dotenv

from .config import IngestConfig
from .pipeline import ingest_document


def main(argv: list[str] | None = None) -> None:
    load_dotenv(Path(__file__).resolve().parents[1] / ".env")

    parser = argparse.ArgumentParser(prog="km-ingest-service")
    subparsers = parser.add_subparsers(dest="command", required=True)

    ingest_parser = subparsers.add_parser("ingest", help="Upload a file, run MinerU, and upload parsed artifacts")
    ingest_parser.add_argument("--file", required=True, help="Local document path")
    ingest_parser.add_argument("--doc-id", help="Optional document ID. Generated when omitted.")
    ingest_parser.add_argument("--manifest", help="Optional path to write manifest JSON")
    ingest_parser.add_argument("--skip-mineru", action="store_true", help="Only upload raw file. Useful for MinIO smoke tests.")

    args = parser.parse_args(argv)
    if args.command == "ingest":
        config = IngestConfig.from_env()
        manifest = ingest_document(
            file_path=Path(args.file),
            config=config,
            doc_id=args.doc_id,
            skip_mineru=args.skip_mineru,
        )
        payload = manifest.to_dict()
        text = json.dumps(payload, ensure_ascii=False, indent=2)
        if args.manifest:
            manifest_path = Path(args.manifest)
            manifest_path.parent.mkdir(parents=True, exist_ok=True)
            manifest_path.write_text(text + "\n", encoding="utf-8")
        sys.stdout.write(text + "\n")


if __name__ == "__main__":
    main()
