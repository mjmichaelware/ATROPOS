#!/usr/bin/env python3
import argparse
import json
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / "src"
if str(SRC) not in sys.path:
    sys.path.insert(0, str(SRC))

from specgraph_foundry.compiler import SpecGraphCompiler
from specgraph_foundry.compiler.compiler_fingerprints import canonical_serialize
from specgraph_foundry.compiler.proof_bundle import verify_proof_bundle


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Compile a source document and emit the existing SpecGraph proof bundle."
    )
    parser.add_argument("source", type=Path)
    parser.add_argument("--project-id", default="specgraph-proof")
    parser.add_argument("--output", type=Path)
    parser.add_argument(
        "--include-requirements",
        action="store_true",
        help="Include accepted atoms beside the proof bundle.",
    )
    parser.add_argument(
        "--verify",
        action="store_true",
        help="Include read-only proof bundle verification results.",
    )
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    source_path = args.source.resolve()
    content = source_path.read_bytes()
    result = SpecGraphCompiler(args.project_id).compile(
        str(source_path),
        content,
    )
    payload = {
        "proof_bundle": result["proof_bundle"],
    }
    if args.verify:
        payload["proof_bundle_verification"] = verify_proof_bundle(
            result["proof_bundle"]
        )
    if args.include_requirements:
        payload["requirements"] = result["requirements"]
        payload["unresolved_records"] = result["unresolved_records"]

    output = canonical_serialize(payload) + b"\n"
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_bytes(output)
    else:
        sys.stdout.buffer.write(output)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
