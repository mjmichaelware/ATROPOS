#!/usr/bin/env bash
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
node --check "$root/npm/bin/atropos.js"
node --check "$root/npm/scripts/postinstall.js"
grep -Fq 'ATROPOS_SKIP_DOWNLOAD' "$root/npm/scripts/postinstall.js"
grep -Fq 'ATROPOS.jar.sha256' "$root/npm/scripts/postinstall.js"
grep -Fq 'process.exit(1)' "$root/npm/scripts/postinstall.js"
grep -Fq 'Nothing was installed' "$root/npm/scripts/postinstall.js"
echo "NPM_INSTALLER_CONTRACT_OK"
