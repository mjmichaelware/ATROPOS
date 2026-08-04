#!/usr/bin/env python3
"""Validate the canonical source-to-code completion registry."""
import json
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
DEFAULT_REGISTRY = ROOT / "docs/completion/ATROPOS_CODE_OBLIGATION_REGISTRY.json"
REQUIRED = {
    "obligationId",
    "requirementId",
    "sourceDocument",
    "sourceCoordinate",
    "sourceHash",
    "canonicalOwner",
    "status",
    "implementationEvidencePaths",
}


def fail(message: str) -> None:
    print(f"TRACEABILITY_GATE_FAILED: {message}", file=sys.stderr)
    raise SystemExit(1)


def main() -> int:
    registry_path = Path(sys.argv[1]).resolve() if len(sys.argv) > 1 else DEFAULT_REGISTRY
    if not registry_path.is_file():
        fail(f"missing registry {registry_path}")
    try:
        payload = json.loads(registry_path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as error:
        fail(f"unreadable registry: {error}")
    records = payload.get("obligations")
    if not isinstance(records, list) or not records:
        fail("registry has no obligations")

    seen = set()
    for record in records:
        if not isinstance(record, dict) or not REQUIRED.issubset(record):
            fail("obligation is missing source-to-code trace fields")
        obligation_id = record["obligationId"]
        if obligation_id in seen:
            fail(f"duplicate obligationId {obligation_id}")
        seen.add(obligation_id)
        source_document = ROOT / record["sourceDocument"]
        if not source_document.is_file():
            fail(f"{obligation_id} references missing source {record['sourceDocument']}")
        if not str(record["sourceCoordinate"]).strip():
            fail(f"{obligation_id} has empty source coordinate")
        if not str(record["sourceHash"]).strip():
            fail(f"{obligation_id} has empty source hash")
        if not str(record["canonicalOwner"]).strip():
            fail(f"{obligation_id} has no canonical owner")
        evidence = record["implementationEvidencePaths"]
        if not isinstance(evidence, list):
            fail(f"{obligation_id} evidence paths are not a list")
        if record["status"] == "WRITTEN":
            if not evidence:
                fail(f"written {obligation_id} has no implementation evidence")
            for path in evidence:
                if not (ROOT / path).exists():
                    fail(f"written {obligation_id} references missing evidence {path}")

    print(f"TRACEABILITY_GATE_OK obligations={len(records)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
