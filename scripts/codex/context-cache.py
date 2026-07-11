#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
import os
import sys
from pathlib import Path

from _lib import (
    atomic_write_json,
    atomic_write_text,
    canonical_json,
    fingerprint,
    sha256_file,
    sha256_text,
    write_atomic_json,
)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Generic deterministic cache helper.")
    sub = parser.add_subparsers(dest="cmd", required=True)

    fp = sub.add_parser("fingerprint", help="Hash a normalized payload.")
    fp.add_argument("--schema-version", type=int, default=1)
    fp.add_argument("--text", action="append", default=[])
    fp.add_argument("--file", action="append", default=[])
    fp.add_argument("--env", action="append", default=[])

    put_json = sub.add_parser("put-json", help="Write JSON atomically from stdin.")
    put_json.add_argument("--dir", required=True)
    put_json.add_argument("--key", required=True)

    get_json = sub.add_parser("get-json", help="Read JSON if present.")
    get_json.add_argument("--dir", required=True)
    get_json.add_argument("--key", required=True)

    put_text = sub.add_parser("put-text", help="Write text atomically from stdin.")
    put_text.add_argument("--dir", required=True)
    put_text.add_argument("--key", required=True)

    get_text = sub.add_parser("get-text", help="Read text if present.")
    get_text.add_argument("--dir", required=True)
    get_text.add_argument("--key", required=True)

    list_cmd = sub.add_parser("list", help="List cached keys in a directory.")
    list_cmd.add_argument("--dir", required=True)

    return parser.parse_args()


def fingerprint_payload(args: argparse.Namespace) -> str:
    files = []
    for file_arg in args.file:
        path = Path(file_arg)
        files.append(
            {
                "path": str(path.resolve()),
                "sha256": sha256_file(path),
                "size_bytes": path.stat().st_size,
            }
        )
    env = {name: os.environ.get(name, "") for name in args.env}
    payload = {
        "schema_version": args.schema_version,
        "text": args.text,
        "files": files,
        "env": env,
    }
    return fingerprint(payload)


def cache_path(root: Path, key: str, suffix: str) -> Path:
    return root / f"{key}{suffix}"


def main() -> int:
    args = parse_args()
    if args.cmd == "fingerprint":
        print(fingerprint_payload(args))
        return 0

    root = Path(args.dir)
    root.mkdir(parents=True, exist_ok=True)

    if args.cmd == "put-json":
        key = args.key
        path = cache_path(root, key, ".json")
        data = json.load(sys.stdin)
        write_atomic_json(path, data)
        print(str(path))
        return 0

    if args.cmd == "get-json":
        path = cache_path(root, args.key, ".json")
        if not path.exists():
            return 1
        print(path.read_text("utf-8"), end="")
        return 0

    if args.cmd == "put-text":
        path = cache_path(root, args.key, ".txt")
        atomic_write_text(path, sys.stdin.read())
        print(str(path))
        return 0

    if args.cmd == "get-text":
        path = cache_path(root, args.key, ".txt")
        if not path.exists():
            return 1
        print(path.read_text("utf-8"), end="")
        return 0

    if args.cmd == "list":
        for path in sorted(Path(args.dir).glob("*")):
            if path.is_file():
                print(path.name)
        return 0

    return 2


if __name__ == "__main__":
    raise SystemExit(main())
