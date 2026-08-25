#!/usr/bin/env python3
"""Keep README's canonical provider environment table derived from descriptors."""

from __future__ import annotations

import argparse
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
REGISTRY = ROOT / "src/main/kotlin/atropos/core/provider/StaticProviderDescriptorRegistry.kt"
README = ROOT / "README.md"
BEGIN = "<!-- BEGIN GENERATED PROVIDER ENVIRONMENT TABLE -->"
END = "<!-- END GENERATED PROVIDER ENVIRONMENT TABLE -->"
ENTRY = re.compile(r'd\("([^"]+)","([^"]+)".*?e\(([^)]*)\)')
ENV = re.compile(r'"([A-Z][A-Z0-9_]*)"')


def generated_block() -> str:
    rows = []
    for match in ENTRY.finditer(REGISTRY.read_text(encoding="utf-8")):
        names = ENV.findall(match.group(3))
        if names:
            rendered = ", ".join(f"`{name}`" for name in names)
            rows.append(f"| {match.group(2)} | {rendered} |")
    if not rows:
        raise SystemExit("provider descriptor registry produced no environment rows")
    return "\n".join(
        [BEGIN, "| Provider | Required environment names |", "| --- | --- |", *rows, END]
    )


def replace_block(document: str, block: str) -> str:
    pattern = re.compile(
        rf"{re.escape(BEGIN)}.*?{re.escape(END)}", re.DOTALL
    )
    if not pattern.search(document):
        raise SystemExit("README is missing generated provider table markers")
    return pattern.sub(block, document, count=1)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--check", action="store_true")
    parser.add_argument("--write", action="store_true")
    args = parser.parse_args()
    if args.check == args.write:
        parser.error("choose exactly one of --check or --write")
    expected = replace_block(README.read_text(encoding="utf-8"), generated_block())
    current = README.read_text(encoding="utf-8")
    if args.check:
        if current != expected:
            raise SystemExit("README provider environment table is stale; run --write")
        print("ATROPOS_PROVIDER_README_TABLE_OK")
    else:
        README.write_text(expected, encoding="utf-8")
        print("ATROPOS_PROVIDER_README_TABLE_WRITTEN")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
