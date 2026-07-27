#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

from _lib import (
    abbreviate,
    ensure_dir,
    indexed_lines_from_text,
    load_index,
    read_text,
    slice_indexed_lines,
)


AUTHORITY_PRECEDENCE = [
    ("1", "ATROPOS CODEX-CLI BUILD BLUEPRINT OVER TIME", "source-query.py phase 0..20"),
    ("2", "ATROPOS Source Documents 1 and 2", "source-query.py authority / source-query.py source"),
    ("3", "latest hierarchy and coding-agent research documents", "source-query.py search Director HR Router territory"),
    ("4", "updated DLOI source-document map", "source-query.py search DLOI address never ingest"),
    ("5", "current repository contracts and proven tests", "git diff --name-only / source-section.py"),
    ("6", "dated implementation contexts and doctor reports", "source-query.py search doctor smoke compile"),
    ("7", "README claims only as historical or aspirational evidence", "source-query.py search README local-first"),
]


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Query the ATROPOS source index.")
    parser.add_argument("--index-root", help="Override the generated index root.")
    parser.add_argument("--json", action="store_true", help="Emit JSON.")
    sub = parser.add_subparsers(dest="cmd", required=True)

    search = sub.add_parser("search", help="Search section text and headings.")
    search.add_argument("terms", nargs="+")
    search.add_argument("--limit", type=int, default=10)
    search.add_argument("--source-id", action="append", dest="source_ids", help="Restrict to source IDs.")

    phase = sub.add_parser("phase", help="Search by canonical phase number.")
    phase.add_argument("number", type=int)
    phase.add_argument("--limit", type=int, default=6)
    phase.add_argument("--source-id", action="append", dest="source_ids")

    source = sub.add_parser("source", help="List sections for a source.")
    source.add_argument("source_id")
    source.add_argument("--limit", type=int, default=25)
    source.add_argument("--source-id", action="append", dest="source_ids")

    dup = sub.add_parser("duplicates", help="List duplicate hashes and supersessions.")
    dup.add_argument("--limit", type=int, default=50)

    inv = sub.add_parser("inventory", help="Show the inventory summary.")
    inv.add_argument("--limit", type=int, default=50)

    auth = sub.add_parser("authority", help="Show authority precedence and queries.")
    auth.add_argument("--limit", type=int, default=20)

    return parser.parse_args()


def load_source_texts(index: dict) -> dict[str, str]:
    cache: dict[str, str] = {}
    for source in index["sources"]:
        cache[source["source_id"]] = read_text(Path(source["normalized_path"]))
    return cache


def source_matches(source: dict, allowed: list[str] | None) -> bool:
    if not allowed:
        return True
    sid = source["source_id"]
    return any(sid.startswith(prefix) for prefix in allowed)


def build_section_excerpt(source: dict, normalized_text: str, section: dict) -> str:
    lines = slice_indexed_lines(normalized_text, source["kind"], section["start_line"], section["end_line"])
    text = "\n".join(line.text for line in lines)
    return text.strip("\n")


def score_section(source: dict, section: dict, text: str, terms: list[str]) -> tuple[int, list[str]]:
    reasons: list[str] = []
    score = 0
    heading = " / ".join(section["heading_path"]) if section["heading_path"] else (section.get("heading") or "")
    haystack = f"{source['original_filename']}\n{heading}\n{text}".lower()
    for term in terms:
        term_l = term.lower()
        if term_l in heading.lower():
            score += 4
            reasons.append(f"heading:{term}")
        if term_l in text.lower():
            score += 2
            reasons.append(f"text:{term}")
        if term_l in source["original_filename"].lower():
            score += 3
            reasons.append(f"filename:{term}")
        if term_l in haystack and term_l not in {term.lower() for term in reasons}:
            score += 1
    return score, sorted(dict.fromkeys(reasons))


def result_record(source: dict, section: dict, excerpt: str, score: int, reasons: list[str]) -> dict:
    coords = []
    if section.get("start_page") is not None:
        coords.append(f"P{section['start_page']}-P{section['end_page']}")
    if section.get("start_paragraph") is not None:
        coords.append(f"para {section['start_paragraph']}-{section['end_paragraph']}")
    return {
        "source_id": source["source_id"],
        "original_filename": source["original_filename"],
        "sha256": source["sha256"],
        "kind": source["kind"],
        "section_id": section["section_id"],
        "heading": section.get("heading"),
        "heading_path": section["heading_path"],
        "line_span": f"{section['start_line']}-{section['end_line']}",
        "coordinates": coords,
        "score": score,
        "reasons": reasons,
        "excerpt": abbreviate(excerpt, 320),
    }


def search_query(index: dict, terms: list[str], source_ids: list[str] | None, limit: int) -> list[dict]:
    texts = load_source_texts(index)
    results: list[dict] = []
    for source in index["sources"]:
        if not source_matches(source, source_ids):
            continue
        normalized = texts[source["source_id"]]
        for section in source["sections"]:
            excerpt = build_section_excerpt(source, normalized, section)
            score, reasons = score_section(source, section, excerpt, terms)
            if not reasons:
                continue
            results.append(result_record(source, section, excerpt, score, reasons))
    results.sort(key=lambda row: (-row["score"], row["source_id"], row["section_id"]))
    return results[:limit]


def print_text_results(title: str, results: list[dict]) -> None:
    print(title)
    if not results:
        print("  (no matches)")
        return
    for idx, row in enumerate(results, 1):
        print(
            f"{idx}. {row['source_id']} {row['original_filename']} [{row['section_id']}] "
            f"score={row['score']} lines={row['line_span']}"
        )
        if row["heading"]:
            print(f"   heading: {row['heading']}")
        if row["coordinates"]:
            print(f"   coords: {', '.join(row['coordinates'])}")
        print(f"   why: {', '.join(row['reasons'])}")
        print(f"   excerpt: {row['excerpt']}")


def phase_terms(number: int) -> list[str]:
    return [f"Phase {number}", f"phase {number}", f"phase {number}:", f"phase {number} goal"]


def source_summary(index: dict, source_id: str) -> dict[str, list[dict]]:
    matches = {}
    for source in index["sources"]:
        if source["source_id"] == source_id or source["source_id"].startswith(source_id):
            matches[source["source_id"]] = source["sections"]
    return matches


def main() -> int:
    args = parse_args()
    index = load_index(args.index_root)

    if args.cmd == "authority":
        payload = {
            "schema_version": index["schema_version"],
            "authority_precedence": [
                {"rank": rank, "authority": authority, "query": query}
                for rank, authority, query in AUTHORITY_PRECEDENCE
            ],
        }
        if args.json:
            print(json.dumps(payload, ensure_ascii=False, indent=2, sort_keys=True))
        else:
            print("Authority precedence")
            for item in payload["authority_precedence"]:
                print(f"{item['rank']}. {item['authority']} -> {item['query']}")
        return 0

    if args.cmd == "inventory":
        rows = []
        for source in index["sources"][: args.limit]:
            rows.append(
                {
                    "source_id": source["source_id"],
                    "original_filename": source["original_filename"],
                    "sha256": source["sha256"],
                    "size_bytes": source["size_bytes"],
                    "kind": source["kind"],
                    "family_key": source["family_key"],
                    "version_key": source["version_key"],
                    "section_count": source["section_count"],
                    "superseded_by": source.get("superseded_by"),
                }
            )
        if args.json:
            print(json.dumps({"sources": rows}, ensure_ascii=False, indent=2, sort_keys=True))
        else:
            print("Inventory")
            for row in rows:
                print(
                    f"{row['source_id']} {row['size_bytes']} {row['sha256']} "
                    f"{row['original_filename']} kind={row['kind']} sections={row['section_count']}"
                )
        return 0

    if args.cmd == "duplicates":
        rows = []
        for sha, source_ids in sorted(index.get("duplicate_hashes", {}).items()):
            rows.append({"sha256": sha, "source_ids": source_ids})
        if args.json:
            print(json.dumps({"duplicates": rows, "superseded_by": index.get("superseded_by", {})}, ensure_ascii=False, indent=2, sort_keys=True))
        else:
            print("Duplicate hashes")
            if not rows:
                print("  (none)")
            for row in rows[: args.limit]:
                print(f"{row['sha256']} -> {', '.join(row['source_ids'])}")
            if index.get("superseded_by"):
                print("Superseded sources")
                for source_id, winner in sorted(index["superseded_by"].items())[: args.limit]:
                    print(f"{source_id} -> {winner}")
        return 0

    if args.cmd == "source":
        source_id = args.source_id
        results = []
        texts = load_source_texts(index)
        for source in index["sources"]:
            if not source["source_id"].startswith(source_id):
                continue
            normalized = texts[source["source_id"]]
            for section in source["sections"][: args.limit]:
                excerpt = build_section_excerpt(source, normalized, section)
                score, reasons = score_section(source, section, excerpt, [source_id, source["original_filename"]])
                results.append(result_record(source, section, excerpt, score, reasons))
        results.sort(key=lambda row: (row["source_id"], row["section_id"]))
        if args.json:
            print(json.dumps({"source_id": source_id, "sections": results}, ensure_ascii=False, indent=2, sort_keys=True))
        else:
            print(f"Source {source_id}")
            if not results:
                print("  (no matches)")
            for row in results:
                print(f"{row['section_id']} {row['line_span']} {row['heading'] or ''}".strip())
                print(f"  {row['excerpt']}")
        return 0

    if args.cmd == "phase":
        terms = phase_terms(args.number)
        results = search_query(index, terms, args.source_ids, args.limit)
        title = f"Phase {args.number}"
        if args.json:
            print(json.dumps({"query": terms, "results": results}, ensure_ascii=False, indent=2, sort_keys=True))
        else:
            print_text_results(title, results)
        return 0

    if args.cmd == "search":
        results = search_query(index, args.terms, args.source_ids, args.limit)
        if args.json:
            print(json.dumps({"query": args.terms, "results": results}, ensure_ascii=False, indent=2, sort_keys=True))
        else:
            print_text_results("Search", results)
        return 0

    return 2


if __name__ == "__main__":
    raise SystemExit(main())
