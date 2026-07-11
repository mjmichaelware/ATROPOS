#!/usr/bin/env python3
from __future__ import annotations

import argparse
import csv
import json
import sys
from collections import Counter, defaultdict
from datetime import datetime, timezone
from pathlib import Path

from _lib import (
    CONTEXT_CACHE_ROOT,
    MANIFEST_PATH,
    SCHEMA_VERSION,
    abbreviate,
    atomic_write_json,
    atomic_write_text,
    combined_file_fingerprint,
    duplicate_groups,
    ensure_dir,
    extract_source,
    fingerprint,
    find_index_root,
    json_line,
    load_manifest,
    normalize_text,
    read_json,
    sectionize,
    sha256_file,
    source_cache_root,
    source_fingerprint,
    sort_entries,
    supersession_groups,
    to_jsonable,
    verify_manifest,
    write_atomic_json,
)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Build the ATROPOS source index.")
    parser.add_argument("--index-root", help="Override the generated index root.")
    parser.add_argument("--strict", action="store_true", help="Fail on any manifest mismatch.")
    return parser.parse_args()


def version_sort_key(entry) -> tuple:
    version = entry.version_key.lower()
    if version.startswith("v"):
        try:
            return (0, tuple(int(part) for part in version[1:].split(".")), entry.source_id)
        except ValueError:
            return (0, (version[1:],), entry.source_id)
    if version.startswith("pass"):
        try:
            return (1, int(version[4:]), entry.source_id)
        except ValueError:
            return (1, version, entry.source_id)
    if version.startswith("part"):
        try:
            return (2, int(version[4:]), entry.source_id)
        except ValueError:
            return (2, version, entry.source_id)
    if version.startswith("20") and len(version) >= 8 and version[4] == "-":
        return (3, version, entry.source_id)
    if version == "unversioned":
        return (9, entry.source_id)
    return (8, version, entry.source_id)


def build_supersession_map(entries):
    superseded_by: dict[str, str] = {}
    family_groups = supersession_groups(entries)
    for family, members in family_groups.items():
        ordered = sorted(members, key=version_sort_key)
        winner = ordered[-1]
        for member in ordered[:-1]:
            superseded_by[member.source_id] = winner.source_id
    return superseded_by


def load_or_extract(entry):
    extraction = extract_source(entry)
    normalized = normalize_text(extraction.text)
    return extraction.__class__(
        source_id=extraction.source_id,
        original_filename=extraction.original_filename,
        sha256=extraction.sha256,
        size_bytes=extraction.size_bytes,
        kind=extraction.kind,
        text=normalized,
        lines=extraction.lines,
        page_count=extraction.page_count,
        paragraph_count=extraction.paragraph_count,
        style_map=extraction.style_map,
    )


def main() -> int:
    args = parse_args()
    index_root = find_index_root(args.index_root)
    ensure_dir(index_root)
    ensure_dir(index_root / "normalized")
    ensure_dir(index_root / "extracted")
    ensure_dir(index_root / "reports")

    entries = sort_entries(load_manifest())
    problems = verify_manifest(entries)
    if problems:
        for problem in problems:
            print(f"MANIFEST_ERROR {problem}", file=sys.stderr)
        if args.strict:
            return 2

    superseded_by = build_supersession_map(entries)
    duplicate_map = duplicate_groups(entries)
    source_records = []
    inventory_rows = []
    total_sections = 0
    total_bytes = 0
    kind_counts: Counter[str] = Counter()

    for entry in entries:
        extraction = load_or_extract(entry)
        sections = sectionize(extraction)
        normalized_path = index_root / "normalized" / entry.sha256[:2] / f"{entry.sha256}.v{SCHEMA_VERSION}.txt"
        meta_path = index_root / "extracted" / entry.sha256[:2] / f"{entry.sha256}.v{SCHEMA_VERSION}.json"
        ensure_dir(normalized_path.parent)
        ensure_dir(meta_path.parent)

        atomic_write_text(normalized_path, extraction.text)

        section_records = []
        for section in sections:
            section_records.append(
                {
                    "section_id": section.section_id,
                    "heading": section.heading,
                    "heading_path": section.heading_path,
                    "heading_level": section.heading_level,
                    "start_line": section.start_line,
                    "end_line": section.end_line,
                    "start_page": section.start_page,
                    "end_page": section.end_page,
                    "start_paragraph": section.start_paragraph,
                    "end_paragraph": section.end_paragraph,
                    "normalized_sha256": section.normalized_sha256,
                    "token_estimate": section.token_estimate,
                    "line_span": f"{section.start_line}-{section.end_line}",
                }
            )

        source_record = {
            "source_id": entry.source_id,
            "original_filename": entry.original_filename,
            "original_path": str(entry.original_path),
            "download_path": entry.download_path,
            "sha256": entry.sha256,
            "size_bytes": entry.size_bytes,
            "kind": extraction.kind,
            "family_key": entry.family_key,
            "version_key": entry.version_key,
            "duplicate_group_size": len(duplicate_map.get(entry.sha256, [])),
            "superseded_by": superseded_by.get(entry.source_id),
            "normalized_path": str(normalized_path),
            "normalized_sha256": sha256_file(normalized_path),
            "line_count": len(extraction.lines),
            "page_count": extraction.page_count,
            "paragraph_count": extraction.paragraph_count,
            "section_count": len(section_records),
            "sections": section_records,
        }
        write_atomic_json(meta_path, source_record)
        source_records.append(source_record)
        total_sections += len(section_records)
        total_bytes += entry.size_bytes
        kind_counts[extraction.kind] += 1
        inventory_rows.append(
            {
                "source_id": entry.source_id,
                "original_filename": entry.original_filename,
                "sha256": entry.sha256,
                "size_bytes": entry.size_bytes,
                "kind": extraction.kind,
                "family_key": entry.family_key,
                "version_key": entry.version_key,
                "duplicate_group_size": len(duplicate_map.get(entry.sha256, [])),
                "superseded_by": superseded_by.get(entry.source_id, ""),
                "normalized_path": str(normalized_path.relative_to(index_root)),
                "section_count": len(section_records),
            }
        )

    manifest_sha = sha256_file(MANIFEST_PATH)
    index = {
        "schema_version": SCHEMA_VERSION,
        "tool_version": "source-index-v1",
        "generated_at": datetime.now(timezone.utc).isoformat(),
        "manifest_path": str(MANIFEST_PATH),
        "manifest_sha256": manifest_sha,
        "source_fingerprint": source_fingerprint(entries),
        "source_count": len(source_records),
        "total_bytes": total_bytes,
        "total_sections": total_sections,
        "kind_counts": dict(sorted(kind_counts.items())),
        "duplicate_hashes": {
            sha: [member.source_id for member in members]
            for sha, members in sorted(duplicate_map.items())
        },
        "superseded_by": superseded_by,
        "sources": source_records,
    }

    index_path = index_root / "index.json"
    atomic_write_json(index_path, index)

    inventory_path = index_root / "inventory.tsv"
    inventory_lines = [
        "\t".join(
            [
                "source_id",
                "original_filename",
                "sha256",
                "size_bytes",
                "kind",
                "family_key",
                "version_key",
                "duplicate_group_size",
                "superseded_by",
                "normalized_path",
                "section_count",
            ]
        )
    ]
    for row in inventory_rows:
        inventory_lines.append(
            "\t".join(
                [
                    str(row["source_id"]),
                    str(row["original_filename"]),
                    str(row["sha256"]),
                    str(row["size_bytes"]),
                    str(row["kind"]),
                    str(row["family_key"]),
                    str(row["version_key"]),
                    str(row["duplicate_group_size"]),
                    str(row["superseded_by"]),
                    str(row["normalized_path"]),
                    str(row["section_count"]),
                ]
            )
        )
    atomic_write_text(inventory_path, "\n".join(inventory_lines) + "\n")

    report_path = index_root / "reports" / "source-index-summary.json"
    atomic_write_json(
        report_path,
        {
            "index_path": str(index_path),
            "inventory_path": str(inventory_path),
            "source_count": len(source_records),
            "total_sections": total_sections,
            "total_bytes": total_bytes,
            "kind_counts": dict(sorted(kind_counts.items())),
            "manifest_sha256": manifest_sha,
            "source_fingerprint": index["source_fingerprint"],
        },
    )

    print(
        "SOURCE_INDEX_OK "
        f"sources={len(source_records)} sections={total_sections} bytes={total_bytes} "
        f"duplicates={len(duplicate_map)} manifest_sha={manifest_sha} fingerprint={index['source_fingerprint']}"
    )
    print(f"INDEX_ROOT {index_root}")
    print(f"INDEX_PATH {index_path}")
    print(f"INVENTORY_PATH {inventory_path}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
