#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SCRIPT="$ROOT/scripts/reproducible-jar-hash.sh"
test -x "$SCRIPT" || chmod +x "$SCRIPT"

tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT
printf 'same-bytes' > "$tmp/one.jar"
cp "$tmp/one.jar" "$tmp/two.jar"
"$SCRIPT" "$tmp/one.jar" "$tmp/two.jar" | grep -Fq ATROPOS_REPRODUCIBLE_JAR_HASH_OK
printf 'different' > "$tmp/two.jar"
if "$SCRIPT" "$tmp/one.jar" "$tmp/two.jar" >/dev/null 2>&1; then
  echo 'reproducibility mismatch was accepted' >&2
  exit 1
fi
printf '%s\n' REPRODUCIBLE_JAR_HASH_TEST_OK
