#!/usr/bin/env bash
# B-17 test: linear-territory-fix.sh contract test
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
TEST_SCRIPT="$SCRIPT_DIR/linear-territory-fix.sh"

test_missing_env() {
  echo "Test: Missing environment variables"
  output=$(LINEAR_API_KEY="" LINEAR_TEAM_ID="" "$TEST_SCRIPT" "TEST-123" 2>&1 || true)
  if echo "$output" | grep -q "LINEAR_API_KEY not set"; then
    echo "PASS: Fails with LINEAR_API_KEY not set"
  else
    echo "FAIL: Expected LINEAR_API_KEY error"
    return 1
  fi
}

test_missing_issue_id() {
  echo "Test: Missing issue ID"
  output=$(LINEAR_API_KEY="test" LINEAR_TEAM_ID="test" "$TEST_SCRIPT" 2>&1 || true)
  if echo "$output" | grep -q "Usage:"; then
    echo "PASS: Shows usage when no issue ID"
  else
    echo "FAIL: Expected usage message"
    return 1
  fi
}

test_dry_run() {
  echo "Test: Dry run mode"
  # Mock the Linear API response
  output=$(LINEAR_API_KEY="test" LINEAR_TEAM_ID="test" \
    "$TEST_SCRIPT" "TEST-123" --dry-run 2>&1 || true)
  if echo "$output" | grep -q "DRY RUN"; then
    echo "PASS: Dry run mode works"
  else
    echo "FAIL: Expected dry run message"
    return 1
  fi
}

test_help() {
  echo "Test: Help flag"
  output=$(LINEAR_API_KEY="test" LINEAR_TEAM_ID="test" "$TEST_SCRIPT" --help 2>&1 || true)
  if echo "$output" | grep -q "Usage:"; then
    echo "PASS: Help shows usage"
  else
    echo "FAIL: Expected usage message"
    return 1
  fi
}

main() {
  echo "Running linear-territory-fix.sh contract tests..."
  test_missing_env
  test_missing_issue_id
  test_dry_run
  test_help
  echo "All contract tests passed!"
}

main