#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
grep -q '^ApkSigner() {' "$ROOT/scripts/setup-signing-secrets.sh"
grep -q '^ApkSigner "\$@"$' "$ROOT/scripts/setup-signing-secrets.sh"
bash -n "$ROOT/scripts/setup-signing-secrets.sh"
echo ApkSigner_TEST_OK
