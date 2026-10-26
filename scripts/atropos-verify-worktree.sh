#!/usr/bin/env bash
set -euo pipefail

if [[ "${GITHUB_ACTIONS:-false}" == "true" ]]; then
  # GitHub is the authoritative Gradle lane. The local fast gate depends on a
  # standalone kotlinc binary that is not part of the hosted Java setup and
  # would fail before the real compile/test evidence can be produced.
  echo "=== HOSTED LANE: SKIP LOCAL KOTLINC FAST GATE ==="
else
  echo "=== FAST CLASSES ==="
  scripts/atropos-fast-gate.sh classes

  echo "=== FULL SMOKE ==="
  scripts/atropos-fast-gate.sh smoke
fi

if [[ "${GITHUB_ACTIONS:-false}" == "true" ]]; then
  echo "=== HOSTED ORPHAN GATE ==="
  python3 scripts/find-orphans.py --fail-on-new

  echo "=== PROVIDER CONNECT CONTRACT ==="
  bash scripts/provider-connect-contract-test.sh

  echo "=== BACKEND ATOM SOURCE CONTRACT ==="
  bash scripts/backend-atom-contract-test.sh

  echo "=== HOSTED COMPILE ==="
  ./gradlew --no-daemon compileJava compileTestJava compileKotlin compileTestKotlin

fi

echo "=== DIFF CHECK ==="
git diff --check

echo "=== NPM INSTALLER CONTRACT ==="
bash scripts/npm-installer-contract-test.sh

echo "=== GITHUB ACTION CONTRACT ==="
bash scripts/atropos-verify-action-contract-test.sh
bash scripts/atropos-verify-action-path-test.sh


echo "=== MCP EXAMPLE CONTRACT ==="
bash scripts/mcp-example-contract-test.sh

echo "=== GITHUB WRITE CONTRACT ==="
bash scripts/github-write-contract-test.sh

echo "ATROPOS_WORKTREE_VERIFY_OK"
