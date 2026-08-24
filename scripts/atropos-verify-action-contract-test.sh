#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ACTION="$ROOT/.github/actions/atropos-verify/action.yml"
WORKFLOW="$ROOT/.github/workflows/atropos-verify-example.yml"

test -s "$ACTION"
grep -Eq '^inputs:' "$ACTION"
grep -Eq '^  working-directory:' "$ACTION"
grep -Eq '^  verify-script:' "$ACTION"
grep -Eq '^outputs:' "$ACTION"
grep -Eq '^  evidence-hashes:' "$ACTION"
grep -Eq 'value: \$\{\{ steps\.verify\.outputs\.evidence_hashes \}\}' "$ACTION"
grep -Eq '^    - id: verify$' "$ACTION"
grep -Fq 'PIPESTATUS[0]' "$ACTION"
grep -Fq 'GITHUB_OUTPUT' "$ACTION"
grep -Fq 'tee "$log_file"' "$ACTION"

printf '%s\n' ATROPOS_VERIFY_ACTION_CONTRACT_OK

test -s "$WORKFLOW"
grep -Eq '^  pull_request:' "$WORKFLOW"
grep -Eq 'actions/checkout@v[0-9]+' "$WORKFLOW"
grep -Eq 'actions/setup-java@v[0-9]+' "$WORKFLOW"
grep -Fq 'uses: ./.github/actions/atropos-verify' "$WORKFLOW"
grep -Fq 'actions/github-script@v' "$WORKFLOW"

printf '%s\n' ATROPOS_VERIFY_WORKFLOW_CONTRACT_OK
