#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUTPUT="$(bash "$ROOT/scripts/github-actions-clean-runner.sh")"
test "$OUTPUT" = GITHUB_ACTIONS_CLEAN_RUNNER_OK
printf '%s\n' GITHUB_ACTIONS_CLEAN_RUNNER_TEST_OK
