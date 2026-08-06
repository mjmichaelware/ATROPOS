#!/usr/bin/env python3
"""Validate the canonical source-to-code completion registry."""
import json
import hashlib
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
    "statusReason",
    "expectedPathsOrSymbols",
    "implementationEvidencePaths",
    "implementationEvidenceSymbols",
}
VALID_STATUSES = {"WRITTEN", "NOT_WRITTEN"}


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
        status = record["status"]
        if status not in VALID_STATUSES:
            fail(f"{obligation_id} has invalid status {status!r}")
        source_document = ROOT / record["sourceDocument"]
        if not source_document.is_file():
            fail(f"{obligation_id} references missing source {record['sourceDocument']}")
        if not str(record["sourceCoordinate"]).strip():
            fail(f"{obligation_id} has empty source coordinate")
        source_hash = str(record["sourceHash"]).strip().lower()
        if not source_hash:
            fail(f"{obligation_id} has empty source hash")
        if len(source_hash) != 64 or any(char not in "0123456789abcdef" for char in source_hash):
            fail(f"{obligation_id} has invalid source hash")
        actual_hash = hashlib.sha256(source_document.read_bytes()).hexdigest()
        if actual_hash != source_hash:
            fail(
                f"{obligation_id} source hash mismatch for {record['sourceDocument']}: "
                f"expected {source_hash}, observed {actual_hash}"
            )
        if not str(record["canonicalOwner"]).strip():
            fail(f"{obligation_id} has no canonical owner")
        expected = record["expectedPathsOrSymbols"]
        if not isinstance(expected, list) or not expected or any(
            not isinstance(value, str) or not value.strip() for value in expected
        ):
            fail(f"{obligation_id} has no expected implementation path or symbol")
        symbols = record["implementationEvidenceSymbols"]
        if not isinstance(symbols, list) or any(
            not isinstance(value, str) or not value.strip() for value in symbols
        ):
            fail(f"{obligation_id} has invalid implementation evidence symbols")
        evidence = record["implementationEvidencePaths"]
        if not isinstance(evidence, list):
            fail(f"{obligation_id} evidence paths are not a list")
        for path in evidence:
            if not isinstance(path, str) or not path.strip():
                fail(f"{obligation_id} has an invalid evidence path")
            resolved = (ROOT / path).resolve()
            if ROOT not in resolved.parents and resolved != ROOT:
                fail(f"{obligation_id} evidence escapes repository root: {path}")
        if status == "WRITTEN":
            if not evidence:
                fail(f"written {obligation_id} has no implementation evidence")
            for path in evidence:
                if not (ROOT / path).exists():
                    fail(f"written {obligation_id} references missing evidence {path}")
        elif not str(record.get("statusReason", "")).strip():
            fail(f"not-written {obligation_id} has no status reason")

    print(f"TRACEABILITY_GATE_OK obligations={len(records)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
