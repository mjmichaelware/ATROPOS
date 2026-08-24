#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ACTION="$ROOT/.github/actions/atropos-verify/action.yml"

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
