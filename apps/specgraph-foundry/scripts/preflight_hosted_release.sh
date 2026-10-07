#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

cd "$(dirname "$0")/.."

export PYTHONPATH="$PWD/src"

echo "=== HOSTED AUDIT CONTRACTS ==="
python scripts/check_hosted_audit_contracts.py

echo "=== POSTGRES BOOLEAN SQL ==="
python scripts/check_postgres_boolean_sql.py

echo "=== COMPLETE COMPILE ==="
python -m compileall -q \
  src \
  scripts \
  tests

echo "=== COMPLETE TEST SUITE ==="
PYTHONWARNINGS=error::ResourceWarning \
python -m unittest discover \
  -s tests \
  -v

echo "=== LICENSE CHECK ==="
python scripts/check_licenses.py

echo "=== DIFF CHECK ==="
git diff --check

echo "HOSTED RELEASE PREFLIGHT PASSED"
