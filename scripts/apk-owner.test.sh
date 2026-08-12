#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
grep -q '^SideloadApk() {' "$ROOT/scripts/apk.sh"
grep -q '^SideloadApk "\$@"$' "$ROOT/scripts/apk.sh"
bash -n "$ROOT/scripts/apk.sh"
echo SideloadApk_TEST_OK
