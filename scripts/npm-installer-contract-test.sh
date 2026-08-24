#!/usr/bin/env bash
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
release="$root/.github/workflows/release.yml"
node --check "$root/npm/bin/atropos.js"
node --check "$root/npm/scripts/postinstall.js"
grep -Fq 'ATROPOS_SKIP_DOWNLOAD' "$root/npm/scripts/postinstall.js"
grep -Fq 'ATROPOS_JAR' "$root/npm/scripts/postinstall.js"
grep -Fq 'installLocalJar' "$root/npm/scripts/postinstall.js"
grep -Fq 'ATROPOS.jar.sha256' "$root/npm/scripts/postinstall.js"
grep -Fq 'process.exit(1)' "$root/npm/scripts/postinstall.js"
grep -Fq 'Nothing was installed' "$root/npm/scripts/postinstall.js"
grep -Fq 'process.env.ATROPOS_JAR' "$root/npm/bin/atropos.js"
grep -Fq 'process.env.ATROPOS_JAVA_OPTS' "$root/npm/bin/atropos.js"
grep -Fq 'spawnSync' "$root/npm/bin/atropos.js"
grep -Fq 'stdio: "inherit"' "$root/npm/bin/atropos.js"
grep -Fq 'if (result.signal)' "$root/npm/bin/atropos.js"
grep -Fq 'process.exit(result.status ?? 0)' "$root/npm/bin/atropos.js"
grep -Fq 'publish-npm:' "$release"
grep -Fq "if: startsWith(github.ref, 'refs/tags/v')" "$release"
grep -Fq 'needs: publish' "$release"
grep -Fq 'NODE_AUTH_TOKEN: ${{ secrets.NPM_TOKEN }}' "$release"
grep -Fq 'npm pkg set version="${GITHUB_REF_NAME#v}"' "$release"
grep -Fq 'npm publish --access public --provenance' "$release"
if grep -Fq 'secrets.NPM_TOKEN !=' "$release"; then
  echo 'NPM_INSTALLER_CONTRACT_FAIL: secrets cannot be used directly in job if expressions' >&2
  exit 1
fi
echo "NPM_INSTALLER_CONTRACT_OK"
