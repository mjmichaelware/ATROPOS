#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
WORKFLOW="$ROOT/.github/workflows/release.yml"

# The installer consumes these exact GitHub release asset names. Keep the
# producer and consumer coupled by a cheap source contract in the release job.
grep -Fq 'releases/download/latest/ATROPOS.jar' "$ROOT/install.sh"
grep -Fq 'build/libs/ATROPOS.jar' "$WORKFLOW"
grep -Fq 'build/libs/ATROPOS.jar.sha256' "$WORKFLOW"
grep -Fq 'sha256sum build/libs/ATROPOS.jar' "$WORKFLOW"
grep -Fq 'tag_name: latest' "$WORKFLOW"
grep -Fq 'files: |' "$WORKFLOW"
grep -Fq 'needs: publish' "$WORKFLOW"
grep -Fq 'backend-atom-contract-test.sh' "$WORKFLOW"
grep -Fq 'github-actions-clean-runner-test.sh' "$WORKFLOW"

printf '%s\n' 'ATROPOS_RELEASE_INSTALLER_CONTRACT_OK'
