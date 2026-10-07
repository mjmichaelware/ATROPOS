#!/usr/bin/env python3
"""Validate the canonical source-to-code completion registry."""
import json
import hashlib
import os
import sys
from datetime import datetime
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
DEFAULT_REGISTRY = ROOT / "docs/completion/ATROPOS_CODE_OBLIGATION_REGISTRY.json"
REQUIRED = {
    "obligationId",
    "requirementId",
    "phase",
    "checkpoint",
    "title",
    "sourceDocument",
    "sourceCoordinate",
    "sourceHash",
    "canonicalOwner",
    "status",
    "statusReason",
    "expectedPathsOrSymbols",
    "implementationEvidencePaths",
    "implementationEvidenceSymbols",
    "lastAuditedHead",
    "lastAuditedAt",
}
VALID_STATUSES = {"WRITTEN", "NOT_WRITTEN"}


def fail(message: str) -> None:
    print(f"TRACEABILITY_GATE_FAILED: {message}", file=sys.stderr)
    raise SystemExit(1)


def safe_repo_file(path: Path) -> bool:
    """Require a regular file whose repository path has no symlink boundary."""
    lexical = Path(os.path.abspath(path))
    try:
        relative = lexical.relative_to(ROOT)
    except ValueError:
        return False
    cursor = ROOT
    for part in relative.parts:
        cursor /= part
        if cursor.is_symlink():
            return False
    return lexical.is_file()


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

    all_ids = {
        record.get("obligationId")
        for record in records
        if isinstance(record, dict) and isinstance(record.get("obligationId"), str)
    }
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
        if not isinstance(record["phase"], int) or record["phase"] < 0:
            fail(f"{obligation_id} has invalid phase {record['phase']!r}")
        if not str(record["checkpoint"]).strip():
            fail(f"{obligation_id} has empty checkpoint")
        if not str(record["title"]).strip():
            fail(f"{obligation_id} has empty title")
        head = str(record["lastAuditedHead"]).strip().lower()
        if not head or len(head) > 64 or any(char not in "0123456789abcdef" for char in head):
            fail(f"{obligation_id} has invalid lastAuditedHead")
        try:
            datetime.fromisoformat(str(record["lastAuditedAt"]).replace("Z", "+00:00"))
        except (TypeError, ValueError):
            fail(f"{obligation_id} has invalid lastAuditedAt")
        duplicate_of = record.get("duplicateOf")
        if duplicate_of is not None:
            if duplicate_of == obligation_id:
                fail(f"{obligation_id} is duplicated by itself")
            if duplicate_of not in all_ids:
                fail(f"{obligation_id} references unknown duplicateOf {duplicate_of!r}")
        source_document = ROOT / record["sourceDocument"]
        if not safe_repo_file(source_document):
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
        if not isinstance(expected, list) or any(
            not isinstance(value, str) or not value.strip() for value in expected
        ):
            fail(f"{obligation_id} has no expected implementation path or symbol")
        if status == "WRITTEN" and not expected:
            fail(f"written {obligation_id} has no expected implementation path or symbol")
        symbols = record["implementationEvidenceSymbols"]
        if not isinstance(symbols, list) or any(
            not isinstance(value, str) or not value.strip() for value in symbols
        ):
            fail(f"{obligation_id} has invalid implementation evidence symbols")
        evidence = record["implementationEvidencePaths"]
        if not isinstance(evidence, list):
            fail(f"{obligation_id} evidence paths are not a list")
        expected_paths = {
            value.split(":", 1)[0]
            for value in expected
            if "/" in value or value.endswith((
                ".kt", ".kts", ".py", ".sh", ".md", ".json", ".properties",
                ".toml", ".yaml", ".yml", ".xml", ".sql", ".ts", ".tsx", ".java"
            ))
        }
        expected_symbols = {
            value for value in expected
            if value not in expected_paths
        }
        if status == "WRITTEN" and expected_paths and expected_paths.isdisjoint(evidence):
            fail(
                f"{obligation_id} evidence does not include a declared implementation path: "
                f"expected={sorted(expected_paths)} evidence={evidence}"
            )
        if status == "WRITTEN" and expected_symbols and expected_symbols.isdisjoint(symbols):
            fail(
                f"{obligation_id} evidence does not include a declared implementation symbol: "
                f"expected={sorted(expected_symbols)} symbols={symbols}"
            )
        evidence_hashes = record.get("implementationEvidenceHashes", {})
        if not isinstance(evidence_hashes, dict):
            fail(f"{obligation_id} evidence hashes are not an object")
        for path in evidence:
            if not isinstance(path, str) or not path.strip():
                fail(f"{obligation_id} has an invalid evidence path")
            resolved = (ROOT / path).resolve()
            if ROOT not in resolved.parents and resolved != ROOT:
                fail(f"{obligation_id} evidence escapes repository root: {path}")
            # A registry cannot contain its own current SHA-256: changing the
            # registry to update that field changes the bytes being hashed.
            # Keep the path/existence checks, but do not demand an impossible
            # recursive digest equality for this one canonical self-reference.
            if resolved == registry_path:
                continue
            expected_hash = str(evidence_hashes.get(path, "")).strip().lower()
            if expected_hash and (len(expected_hash) != 64 or any(char not in "0123456789abcdef" for char in expected_hash)):
                fail(f"{obligation_id} has invalid evidence hash for {path}")
            evidence_path = ROOT / path
            if evidence_path.exists() and not safe_repo_file(evidence_path):
                fail(f"{obligation_id} evidence path crosses a symlink or is not a file: {path}")
            if status == "WRITTEN" and expected_hash and evidence_path.is_file():
                observed_hash = hashlib.sha256(evidence_path.read_bytes()).hexdigest()
                if observed_hash != expected_hash:
                    fail(f"{obligation_id} evidence hash mismatch for {path}")
        if status == "WRITTEN":
            if not evidence:
                fail(f"written {obligation_id} has no implementation evidence")
            for path in evidence:
                if not safe_repo_file(ROOT / path):
                    fail(f"written {obligation_id} references missing evidence {path}")
        elif not str(record.get("statusReason", "")).strip():
            fail(f"not-written {obligation_id} has no status reason")

    print(f"TRACEABILITY_GATE_OK obligations={len(records)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
