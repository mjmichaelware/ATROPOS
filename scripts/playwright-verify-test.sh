#!/usr/bin/env bash
# B-19 test: playwright-verify.sh contract test
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TEST_SCRIPT="$SCRIPT_DIR/playwright-verify.sh"

test_help() {
  echo "Test: Help flag"
  output=$("$TEST_SCRIPT" --help 2>&1 || true)
  if echo "$output" | grep -q "Usage:"; then
    echo "PASS: Help shows usage"
  else
    echo "FAIL: Expected usage message"
    return 1
  fi
}

test_missing_project_dir() {
  echo "Test: Missing project directory"
  output=$("$TEST_SCRIPT" 2>&1 || true)
  if echo "$output" | grep -q "Project directory required"; then
    echo "PASS: Fails when project dir missing"
  else
    echo "FAIL: Expected project dir error"
    return 1
  fi
}

test_missing_package_json() {
  echo "Test: Missing package.json"
  TEMP_DIR=$(mktemp -d)
  output=$("$TEST_SCRIPT" "$TEMP_DIR" 2>&1 || true)
  rm -rf "$TEMP_DIR"
  if echo "$output" | grep -q "No package.json found"; then
    echo "PASS: Fails when package.json missing"
  else
    echo "FAIL: Expected package.json error"
    return 1
  fi
}

test_help_flag() {
  echo "Test: Help flag"
  output=$("$TEST_SCRIPT" --help 2>&1 || true)
  if echo "$output" | grep -q "Verifies an App Factory"; then
    echo "PASS: Help shows verification description"
  else
    echo "FAIL: Expected verification description"
    return 1
  fi
}

test_browser_option() {
  echo "Test: Browser option parsing"
  TEMP_DIR=$(mktemp -d)
  mkdir -p "$TEMP_DIR/node_modules/@playwright"
  echo '{}' > "$TEMP_DIR/package.json"
  output=$("$TEST_SCRIPT" --browser firefox "$TEMP_DIR" 2>&1 || true)
  rm -rf "$TEMP_DIR"
  # Should not fail on browser option parsing
  if ! echo "$output" | grep -q "invalid option.*browser"; then
    echo "PASS: Browser option accepted"
  else
    echo "FAIL: Browser option rejected"
    return 1
  fi
}

test_output_option() {
  echo "Test: Output directory option"
  TEMP_DIR=$(mktemp -d)
  mkdir -p "$TEMP_DIR/node_modules/@playwright"
  echo '{}' > "$TEMP_DIR/package.json"
  output=$("$TEST_SCRIPT" --output /tmp/test-results "$TEMP_DIR" 2>&1 || true)
  rm -rf "$TEMP_DIR"
  if ! echo "$output" | grep -q "invalid option.*output"; then
    echo "PASS: Output option accepted"
  else
    echo "FAIL: Output option rejected"
    return 1
  fi
}

main() {
  echo "Running playwright-verify.sh contract tests..."
  test_help
  test_missing_project_dir
  test_missing_package_json
  test_help_flag
  test_browser_option
  test_output_option
  echo "All contract tests passed!"
}

main