#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
WORKFLOW="$ROOT/.github/workflows/compile-gate.yml"
VERIFY="$ROOT/scripts/atropos-verify-worktree.sh"

extract() {
  grep -oE 'atropos\.[A-Za-z0-9_.]*Test' "$1" | sort -u
}

workflow_tests="$(extract "$WORKFLOW")"
verify_tests="$(extract "$VERIFY")"

test -n "$workflow_tests"
test -n "$verify_tests"

if [[ "$workflow_tests" != "$verify_tests" ]]; then
  echo "hosted test selector drift: compile-gate.yml and atropos-verify-worktree.sh differ" >&2
  diff -u <(printf '%s\n' "$workflow_tests") <(printf '%s\n' "$verify_tests") >&2 || true
  exit 1
fi

while IFS= read -r test_id; do
  test_path="$ROOT/src/test/kotlin/${test_id//.//}.kt"
  if [[ ! -s "$test_path" ]]; then
    echo "hosted test selector has no source file: $test_id -> $test_path" >&2
    exit 1
  fi
done <<< "$workflow_tests"

printf 'ATROPOS_HOSTED_TEST_SELECTOR_CONTRACT_OK (%s tests)\n' "$(printf '%s\n' "$workflow_tests" | wc -l | tr -d ' ')"
