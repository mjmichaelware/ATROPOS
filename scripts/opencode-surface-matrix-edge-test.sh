#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MATRIX="$ROOT/docs/ui-parity/OPENCODE_COMPLETE_SURFACE_MATRIX.json"

python3 - "$MATRIX" <<'PY'
import json
import sys
from pathlib import Path

path = Path(sys.argv[1])
document = json.loads(path.read_text(encoding="utf-8"))
rows = document.get("rows")
if not isinstance(rows, list) or not rows:
    raise SystemExit("UI_PARITY_MATRIX_EDGE_FAIL rows are missing")

allowed_statuses = {
    "DISCOVERED",
    "MAPPED",
    "IMPLEMENTED",
    "WIRED",
    "REACHABLE",
    "STATE_CONNECTED",
    "RESPONSIVE",
    "ACCESSIBLE",
    "PERSISTENCE_VERIFIED",
    "BEHAVIOR_TESTED",
    "VISUALLY_COMPARED",
    "FAILURE_TESTED",
    "INDEPENDENTLY_VERIFIED",
    "BLOCKED_NO_TARGET_SURFACE",
}
required = {"reference_id", "client", "kind", "source_path", "tests", "evidence", "status"}
seen = set()
for row in rows:
    if not required.issubset(row):
        missing = sorted(required.difference(row))
        raise SystemExit(f"UI_PARITY_MATRIX_EDGE_FAIL missing={missing}")
    reference = row["reference_id"]
    if not isinstance(reference, str) or not reference or reference in seen:
        raise SystemExit(f"UI_PARITY_MATRIX_EDGE_FAIL duplicate_or_blank_reference={reference!r}")
    seen.add(reference)
    status = row["status"]
    if status not in allowed_statuses:
        raise SystemExit(f"UI_PARITY_MATRIX_EDGE_FAIL unknown_status={status!r}")
    if status == "INDEPENDENTLY_VERIFIED" and (not row["tests"] or not row["evidence"]):
        raise SystemExit(f"UI_PARITY_MATRIX_EDGE_FAIL verified_without_tests_or_evidence={reference}")

print(f"UI_PARITY_MATRIX_EDGE_OK rows={len(rows)} verified_with_evidence=0_or_proven")
PY
