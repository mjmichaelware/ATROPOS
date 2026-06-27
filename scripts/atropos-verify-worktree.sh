#!/usr/bin/env bash
set -euo pipefail

echo "=== FAST CLASSES ==="
scripts/atropos-fast-gate.sh classes

echo "=== FULL SMOKE ==="
scripts/atropos-fast-gate.sh smoke

echo "=== DIFF CHECK ==="
git diff --check

echo "ATROPOS_WORKTREE_VERIFY_OK"
