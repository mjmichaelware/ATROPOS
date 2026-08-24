#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ACTION="$ROOT/.github/actions/atropos-verify/action.yml"

# This is a shell-level contract for the guard embedded in the composite
# action. It intentionally does not execute GitHub Actions locally.
guard='case "$VERIFY_SCRIPT" in'
grep -Fq "$guard" "$ACTION"
grep -Fq '/*|../*|*/../*' "$ACTION"
grep -Fq 'test -f "$VERIFY_SCRIPT"' "$ACTION"
printf '%s\n' ATROPOS_VERIFY_ACTION_PATH_CONTRACT_OK
