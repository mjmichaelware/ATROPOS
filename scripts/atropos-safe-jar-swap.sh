#!/usr/bin/env bash
set -euo pipefail

NEW_JAR="${1:?usage: scripts/atropos-safe-jar-swap.sh NEW_JAR}"

if [ ! -f "$NEW_JAR" ]; then
  echo "missing jar: $NEW_JAR" >&2
  exit 2
fi

mkdir -p .atropos/backups

if [ -f atropos.jar ]; then
  cp atropos.jar ".atropos/backups/atropos.$(date +%s).jar"
fi

cp "$NEW_JAR" atropos.jar
echo "ATROPOS_SAFE_JAR_SWAP_OK"
