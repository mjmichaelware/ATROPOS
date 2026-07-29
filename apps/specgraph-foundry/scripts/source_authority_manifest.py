#!/usr/bin/env python3
import argparse
import json
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / "src"
if str(SRC) not in sys.path:
    sys.path.insert(0, str(SRC))

from specgraph_foundry.compiler.compiler_fingerprints import canonical_serialize
from specgraph_foundry.compiler.source_authority import AuthorityRegistry, SourceAuthority


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Build a deterministic SourceAuthority registry manifest from JSON."
    )
    parser.add_argument("registry_json", type=Path)
    parser.add_argument("--output", type=Path)
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    payload = json.loads(args.registry_json.read_text(encoding="utf-8"))
    registry = AuthorityRegistry()
    for item in payload.get("authorities", []):
        registry.register_authority(SourceAuthority(
            document_id=item["document_id"],
            tier=int(item["tier"]),
            version=item["version"],
            effective_date=item["effective_date"],
            owner=item["owner"],
            is_approved=bool(item.get("is_approved", True)),
            artifact_sha256=item.get("artifact_sha256"),
        ))
    for item in payload.get("supersessions", []):
        registry.register_supersession(
            item["newer_doc_id"],
            item["older_doc_id"],
        )

    output = canonical_serialize(registry.to_manifest()) + b"\n"
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_bytes(output)
    else:
        sys.stdout.buffer.write(output)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
