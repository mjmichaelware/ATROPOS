#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 2 ]]; then
  printf 'usage: %s <first-jar> <second-jar>\n' "$0" >&2
  exit 2
fi

first=$1
second=$2
test -s "$first"
test -s "$second"

first_hash=$(sha256sum "$first" | awk '{print $1}')
second_hash=$(sha256sum "$second" | awk '{print $1}')
if [[ "$first_hash" != "$second_hash" ]]; then
  printf 'REPRODUCIBLE_JAR_HASH_MISMATCH first=%s second=%s\n' "$first_hash" "$second_hash" >&2
  exit 1
fi

printf 'ATROPOS_REPRODUCIBLE_JAR_HASH_OK hash=%s\n' "$first_hash"
