#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
WORKFLOW="$ROOT/.github/workflows/compile-gate.yml"
VERIFY="$ROOT/scripts/atropos-verify-worktree.sh"

extract() {
  grep -oE 'atropos\.[A-Za-z0-9_.]*Tests?' "$1" | sort -u
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
    # Kotlin test filenames are conventionally class names, but this tree has
    # a small number of valid declarations grouped in a differently named
    # source file. Resolve those by declaration content rather than dropping
    # their hosted coverage or forcing a filesystem rename.
    test_class="${test_id##*.}"
    if ! rg -l --glob '*.kt' "\\b(class|object|interface)[[:space:]]+$test_class\\b" "$ROOT/src/test/kotlin" | grep -q .; then
      echo "hosted test selector has no source declaration: $test_id -> $test_path" >&2
      exit 1
    fi
  fi
done <<< "$workflow_tests"

# Every backend test source must be named by the hosted verifier. UI tests are
# intentionally outside this backend-only lane; all core, bridge, and CLI
# non-UI tests belong to the focused hosted proof set.
backend_test_sources="$(mktemp)"
trap 'rm -f "$backend_test_sources"' EXIT
find "$ROOT/src/test/kotlin/atropos/core" \
     "$ROOT/src/test/kotlin/atropos/bridge" \
     "$ROOT/src/test/kotlin/atropos/cli" \
     -type f -name '*Test.kt' -print | sort > "$backend_test_sources"
while IFS= read -r test_path; do
  relative="${test_path#"$ROOT/src/test/kotlin/"}"
  test_id="${relative%.kt}"
  test_id="${test_id//\//.}"
  case "$test_id" in
    atropos.cli.ui.*) continue ;;
  esac
  if ! grep -Fq -- "$test_id" "$VERIFY"; then
    echo "backend test source is outside hosted selector: $test_id" >&2
    exit 1
  fi
done < "$backend_test_sources"

printf 'ATROPOS_HOSTED_TEST_SELECTOR_CONTRACT_OK (%s tests)\n' "$(printf '%s\n' "$workflow_tests" | wc -l | tr -d ' ')"
