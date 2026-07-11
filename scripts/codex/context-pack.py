#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
import subprocess
import sys
from dataclasses import dataclass
from pathlib import Path

from _lib import (
    REPO_ROOT,
    abbreviate,
    estimate_tokens,
    ensure_dir,
    fingerprint,
    load_json,
    read_text,
    sha256_file,
    sha256_text,
    write_atomic_json,
)


AUTHORITY_PROFILES = {
    "bootstrap": [("phase", "0"), ("phase", "1"), ("phase", "11"), ("phase", "19"), ("phase", "20")],
    "phase11": [("phase", "11")],
    "provider-grid": [("search", "free-first"), ("search", "RoutePolicy"), ("search", "paid locked"), ("search", "paid emergency"), ("search", "configured does not mean verified")],
    "dloi-ast": [("search", "DLOI"), ("search", "address never ingest"), ("search", "AST symbol graph"), ("search", "Tree-sitter")],
    "hierarchy": [("search", "Director"), ("search", "territory"), ("search", "HR Router"), ("search", "Auditor"), ("search", "Custodian"), ("search", "Manager"), ("search", "Specialist"), ("search", "Worker")],
    "memory-policy": [("phase", "9"), ("phase", "10"), ("phase", "11")],
    "acceptance": [("search", "E(DELTA)=0"), ("search", "compile narrowly"), ("search", "context export"), ("search", "verify before commit")],
    "resume": [("search", "queue"), ("search", "context export"), ("search", "resume"), ("search", "handoff")],
}


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Build bounded ATROPOS context packs.")
    parser.add_argument("--index-root", help="Override the source index root.")
    parser.add_argument("--authority", action="append", default=[], help="Authority profile to include.")
    parser.add_argument("--file", action="append", default=[], help="Repository file to include.")
    parser.add_argument("--symbol", action="append", default=[], help="Symbol name to search for in changed files.")
    parser.add_argument("--evidence", action="append", default=[], help="Literal failure evidence text.")
    parser.add_argument("--evidence-file", action="append", default=[], help="File containing failure evidence.")
    parser.add_argument("--max-bytes", type=int, default=12000)
    parser.add_argument("--max-tokens", type=int, default=3000)
    parser.add_argument("--out", help="Write pack markdown here instead of stdout.")
    parser.add_argument("--json-out", help="Write pack manifest JSON here.")
    parser.add_argument("--include-changed", action="store_true", help="Pull changed files from git diff when no files are supplied.")
    return parser.parse_args()


def run_query(kind: str, value: str, index_root: str | None = None) -> list[dict]:
    cmd = [sys.executable, str(REPO_ROOT / "scripts/codex/source-query.py"), "--json"]
    if index_root:
        cmd.extend(["--index-root", index_root])
    if kind == "phase":
        cmd.extend(["phase", value])
    else:
        cmd.extend(["search", value])
    payload = json.loads(subprocess.check_output(cmd, text=True))
    return payload["results"]


def collect_authority_sections(authorities: list[str], index_root: str | None = None) -> list[dict]:
    sections: list[dict] = []
    seen = set()
    for authority in authorities:
        profile = AUTHORITY_PROFILES.get(authority, [("search", authority)])
        for kind, value in profile:
            results = run_query(kind, value, index_root=index_root)
            for result in results[:3]:
                key = (result["source_id"], result["section_id"])
                if key in seen:
                    continue
                seen.add(key)
                result = dict(result)
                result["authority"] = authority
                sections.append(result)
    return sections


def git_changed_files() -> list[str]:
    tracked = subprocess.check_output(["git", "diff", "--name-only", "--diff-filter=ACMRTUXB"], text=True).splitlines()
    untracked = subprocess.check_output(["git", "ls-files", "--others", "--exclude-standard"], text=True).splitlines()
    files = [line.strip() for line in tracked + untracked if line.strip()]
    seen = set()
    out = []
    for item in files:
        if item in seen:
            continue
        seen.add(item)
        out.append(item)
    return out


def read_symbol_hits(symbols: list[str], files: list[str], limit_bytes: int) -> list[dict]:
    if not symbols:
        return []
    targets = files or git_changed_files()
    results: list[dict] = []
    for symbol in symbols:
        for file in targets:
            path = REPO_ROOT / file
            if not path.exists() or not path.is_file():
                continue
            text = path.read_text("utf-8", errors="replace")
            if symbol.lower() not in text.lower():
                continue
            for lineno, line in enumerate(text.splitlines(), 1):
                if symbol.lower() in line.lower():
                    excerpt = f"{file}:{lineno}: {abbreviate(line, 240)}"
                    results.append({"symbol": symbol, "file": file, "excerpt": excerpt})
                    if sum(len(item["excerpt"].encode("utf-8")) for item in results) >= limit_bytes:
                        return results
                    break
    return results


def read_file_excerpt(file: str, max_lines: int = 80) -> dict | None:
    path = REPO_ROOT / file
    if not path.exists() or not path.is_file():
        return None
    text = path.read_text("utf-8", errors="replace")
    lines = text.splitlines()[:max_lines]
    return {
        "file": file,
        "sha256": sha256_file(path),
        "size_bytes": path.stat().st_size,
        "excerpt": "\n".join(lines).rstrip("\n"),
    }


def add_part(parts: list[dict], part: dict, budget: dict[str, int]) -> bool:
    text = part.get("text", "")
    size = len(text.encode("utf-8"))
    tokens = estimate_tokens(text)
    if budget["bytes"] + size > budget["max_bytes"] or budget["tokens"] + tokens > budget["max_tokens"]:
        return False
    budget["bytes"] += size
    budget["tokens"] += tokens
    parts.append(part)
    return True


def main() -> int:
    args = parse_args()
    selected_files = list(args.file)
    if not selected_files and args.include_changed:
        selected_files = git_changed_files()

    parts: list[dict] = []
    budget = {"bytes": 0, "tokens": 0, "max_bytes": args.max_bytes, "max_tokens": args.max_tokens}
    metadata = {
        "schema_version": 1,
        "selected_authorities": args.authority,
        "selected_files": selected_files,
        "selected_symbols": args.symbol,
        "evidence_sources": args.evidence_file,
        "budget": {"max_bytes": args.max_bytes, "max_tokens": args.max_tokens},
    }

    for section in collect_authority_sections(args.authority, index_root=args.index_root):
        text = (
            f"Authority: {section['authority']}\n"
            f"{section['source_id']} {section['original_filename']} [{section['section_id']}]\n"
            f"{section['heading'] or ''}\n"
            f"{section['excerpt']}\n"
        )
        part = {
            "kind": "authority",
            "authority": section["authority"],
            "source_id": section["source_id"],
            "section_id": section["section_id"],
            "text": text,
        }
        if not add_part(parts, part, budget):
            break

    for file in selected_files:
        excerpt = read_file_excerpt(file)
        if not excerpt:
            continue
        text = f"File: {file}\nsha256: {excerpt['sha256']}\nsize_bytes: {excerpt['size_bytes']}\n{excerpt['excerpt']}\n"
        if not add_part(parts, {"kind": "file", "file": file, "text": text}, budget):
            break

    for item in read_symbol_hits(args.symbol, selected_files, args.max_bytes):
        text = f"Symbol: {item['symbol']}\n{item['excerpt']}\n"
        if not add_part(parts, {"kind": "symbol", **item, "text": text}, budget):
            break

    for evidence in args.evidence:
        text = f"Evidence:\n{abbreviate(evidence, 600)}\n"
        if not add_part(parts, {"kind": "evidence", "text": text}, budget):
            break

    for evidence_file in args.evidence_file:
        path = REPO_ROOT / evidence_file
        if not path.exists():
            continue
        text = path.read_text("utf-8", errors="replace")
        if not add_part(
            parts,
            {
                "kind": "evidence-file",
                "file": evidence_file,
                "sha256": sha256_file(path),
                "size_bytes": path.stat().st_size,
                "text": f"Evidence file: {evidence_file}\n{text[:2000]}\n",
            },
            budget,
        ):
            break

    manifest = {
        "metadata": metadata,
        "budget": budget,
        "parts": [
            {k: v for k, v in part.items() if k != "text"} for part in parts
        ],
        "fingerprint": fingerprint({"metadata": metadata, "parts": [
            {k: v for k, v in part.items() if k != "text"} for part in parts
        ]}),
    }

    md_lines = [
        "# ATROPOS Context Pack",
        "",
        f"- selected authorities: {', '.join(args.authority) or '(none)'}",
        f"- selected files: {', '.join(selected_files) or '(none)'}",
        f"- bytes used: {budget['bytes']} / {budget['max_bytes']}",
        f"- token estimate: {budget['tokens']} / {budget['max_tokens']}",
        "",
    ]
    for part in parts:
        md_lines.append(f"## {part['kind']}")
        md_lines.append("")
        md_lines.append(part["text"].rstrip())
        md_lines.append("")
    markdown = "\n".join(md_lines).rstrip() + "\n"

    if args.out:
        path = Path(args.out)
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(markdown, encoding="utf-8")
    else:
        sys.stdout.write(markdown)

    if args.json_out:
        path = Path(args.json_out)
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(json.dumps(manifest, ensure_ascii=False, indent=2, sort_keys=True) + "\n", encoding="utf-8")

    print(
        f"CONTEXT_PACK_OK parts={len(parts)} bytes={budget['bytes']} tokens={budget['tokens']} fingerprint={manifest['fingerprint']}",
        file=sys.stderr,
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
