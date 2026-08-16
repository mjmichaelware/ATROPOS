#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SCRIPT="$ROOT/scripts/package-installers.sh"
TMP="$(mktemp -d "${TMPDIR:-/tmp}/atropos-installers.XXXXXX")"
trap 'rm -rf "$TMP"' EXIT

printf 'placeholder jar\n' > "$TMP/ATROPOS.jar"
bash "$SCRIPT" "$TMP/ATROPOS.jar" "$TMP/out" >/dev/null
test -f "$TMP/out/atropos.rb"
test -f "$TMP/out/atropos-scoop.json"
test -f "$TMP/out/deb/atropos_0.1.0_all/DEBIAN/control"
grep -Fq 'Package: atropos' "$TMP/out/deb/atropos_0.1.0_all/DEBIAN/control"
grep -Fq 'class Atropos' "$TMP/out/atropos.rb"
grep -Fq '"version": "0.1.0"' "$TMP/out/atropos-scoop.json"

printf '%s\n' 'ATROPOS_INSTALLER_CONTRACT_OK'
