#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
INSTALLER="$ROOT/install.sh"

grep -Fq 'mkdir -p "$PREFIX" "$PREFIX/provider" "$BIN_DIR"' "$INSTALLER"
grep -Fq "printf '%s\\n' '{}' > \"\$PREFIX/config.json\"" "$INSTALLER"
grep -Fq "printf '%s\\n' '[]' > \"\$PREFIX/provider/providers.json\"" "$INSTALLER"
grep -Fq '"$BIN_DIR/atropos" --health' "$INSTALLER"
grep -Fq 'doctor: PASS' "$INSTALLER"

printf '%s\n' 'ATROPOS_INSTALL_CONTRACT_OK'
