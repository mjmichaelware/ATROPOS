#!/usr/bin/env bash
set -euo pipefail

ROOT="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd -P)"
VALIDATOR="$ROOT/scripts/source-to-code-trace-gate.py"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

python3 "$VALIDATOR" >/dev/null

python3 - "$ROOT/docs/completion/ATROPOS_CODE_OBLIGATION_REGISTRY.json" "$TMP/bad.json" <<'PY'
import json
import sys

source, target = sys.argv[1:]
payload = json.load(open(source, encoding="utf-8"))
payload["obligations"][0]["sourceCoordinate"] = ""
json.dump(payload, open(target, "w", encoding="utf-8"))
PY

if python3 "$VALIDATOR" "$TMP/bad.json" >/dev/null 2>&1; then
  echo "traceability negative case unexpectedly passed" >&2
  exit 1
fi

echo "SOURCE_TO_CODE_TRACE_GATE_TEST_OK"
