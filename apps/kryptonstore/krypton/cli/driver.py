"""CLI Driver: Command-line administration tool."""
import argparse
import sys
from pathlib import Path
from typing import List, Optional
from krypton.storage.engine import LSMStorageEngine

def create_parser() -> argparse.ArgumentParser:
    p = argparse.ArgumentParser(description="KryptonStore CLI")
    p.add_argument("--node-id", default="node-1")
    p.add_argument("--peers", default="")
    p.add_argument("--data-dir", default="./data")
    sub = p.add_subparsers(dest="command")
    g = sub.add_parser("get"); g.add_argument("key")
    pt = sub.add_parser("put"); pt.add_argument("key"); pt.add_argument("value")
    d = sub.add_parser("delete"); d.add_argument("key")
    sub.add_parser("status")
    return p

def run_cli(args: Optional[List[str]] = None) -> int:
    parser = create_parser()
    parsed = parser.parse_args(args)
    if not parsed.command or parsed.command == "status":
        print(f"KryptonStore node={parsed.node_id} ready")
        return 0
    engine = LSMStorageEngine(Path(parsed.data_dir))
    if parsed.command == "get":
        v = engine.get(parsed.key)
        if v is None:
            print(f"KEY_NOT_FOUND: {parsed.key}", file=sys.stderr); return 1
        print(v.decode("utf-8", errors="replace")); return 0
    elif parsed.command == "put":
        engine.put(parsed.key, parsed.value.encode("utf-8"))
        print(f"OK: {parsed.key} written"); return 0
    elif parsed.command == "delete":
        engine.delete(parsed.key)
        print(f"OK: {parsed.key} deleted"); return 0
    return 0

if __name__ == "__main__":
    sys.exit(run_cli())
