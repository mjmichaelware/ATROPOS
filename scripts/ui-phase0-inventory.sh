#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

ROOT="${1:-$HOME/ATROPOS}"
cd "$ROOT"

OUT_DIR="docs/ui-parity/phase0"
mkdir -p "$OUT_DIR"

ALL="$PREFIX/tmp/atropos-ui-all.$$"
ATROPOS_TUI="$OUT_DIR/ATROPOS_TUI_PATHS.txt"
ATROPOS_WEB="$OUT_DIR/ATROPOS_WEB_PATHS.txt"
PARITY="$OUT_DIR/UI_PARITY_PATHS.txt"
HASHES="$OUT_DIR/UI_PATH_FINGERPRINTS.sha256"
SUMMARY="$OUT_DIR/UI_INVENTORY_SUMMARY.txt"

trap 'rm -f "$ALL"' EXIT

git ls-files | sort -u > "$ALL"

grep '^src/main/kotlin/atropos/cli/' "$ALL" > "$ATROPOS_TUI" || true
grep '^apps/web/' "$ALL" > "$ATROPOS_WEB" || true
grep '^docs/ui-parity/' "$ALL" > "$PARITY" || true

{
  cat "$ATROPOS_TUI"
  cat "$ATROPOS_WEB"
  cat "$PARITY"
} |
sort -u |
while IFS= read -r file; do
  [ -f "$file" ] && sha256sum "$file"
done > "$HASHES"

{
  echo "ATROPOS UI PHASE 0 INVENTORY"
  echo "Generated: $(date -Iseconds)"
  echo "Branch: $(git branch --show-current)"
  echo "HEAD: $(git rev-parse HEAD)"
  echo
  echo "COUNTS"
  printf "ATROPOS CLI/TUI tracked paths: "
  wc -l < "$ATROPOS_TUI"
  printf "ATROPOS Web tracked paths:     "
  wc -l < "$ATROPOS_WEB"
  printf "Canonical Web tracked paths:    "
  wc -l < "$ATROPOS_WEB"
  printf "UI parity tracked paths:       "
  wc -l < "$PARITY"
  printf "Fingerprinted files:           "
  wc -l < "$HASHES"
  echo
  echo "DIRTY PATHS"
  git status --short
} > "$SUMMARY"

cat "$SUMMARY"
