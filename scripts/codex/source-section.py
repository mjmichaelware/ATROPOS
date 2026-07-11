#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
from pathlib import Path

from _lib import abbreviate, load_index, read_text, slice_indexed_lines


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Print one exact indexed source section.")
    parser.add_argument("source_id")
    parser.add_argument("section_id")
    parser.add_argument("--index-root")
    parser.add_argument("--json", action="store_true")
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    index = load_index(args.index_root)
    source = next((item for item in index["sources"] if item["source_id"].startswith(args.source_id)), None)
    if source is None:
        raise SystemExit(f"unknown source id: {args.source_id}")
    section = next((item for item in source["sections"] if item["section_id"] == args.section_id), None)
    if section is None:
        raise SystemExit(f"unknown section id: {args.section_id}")

    normalized_text = read_text(Path(source["normalized_path"]))
    lines = slice_indexed_lines(normalized_text, source["kind"], section["start_line"], section["end_line"])
    text = "\n".join(line.text for line in lines).rstrip("\n")
    payload = {
        "source_id": source["source_id"],
        "original_filename": source["original_filename"],
        "sha256": source["sha256"],
        "size_bytes": source["size_bytes"],
        "kind": source["kind"],
        "section_id": section["section_id"],
        "heading": section.get("heading"),
        "heading_path": section["heading_path"],
        "line_span": f"{section['start_line']}-{section['end_line']}",
        "page_span": None if section.get("start_page") is None else f"{section['start_page']}-{section['end_page']}",
        "paragraph_span": None
        if section.get("start_paragraph") is None
        else f"{section['start_paragraph']}-{section['end_paragraph']}",
        "normalized_sha256": section["normalized_sha256"],
        "token_estimate": section["token_estimate"],
        "text": text,
    }

    if args.json:
        print(json.dumps(payload, ensure_ascii=False, indent=2, sort_keys=True))
    else:
        print(f"{payload['source_id']} {payload['original_filename']} [{payload['section_id']}]")
        if payload["heading"]:
            print(f"heading: {payload['heading']}")
        print(f"line_span: {payload['line_span']}")
        if payload["page_span"]:
            print(f"page_span: {payload['page_span']}")
        if payload["paragraph_span"]:
            print(f"paragraph_span: {payload['paragraph_span']}")
        print(f"normalized_sha256: {payload['normalized_sha256']}")
        print(f"token_estimate: {payload['token_estimate']}")
        print()
        print(payload["text"])
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
