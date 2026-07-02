#!/usr/bin/env bash
set -euo pipefail

JAR="${1:-atropos.jar}"

if [ ! -f "$JAR" ]; then
  echo "missing jar: $JAR" >&2
  exit 2
fi

OUT="$(printf '/providers descriptors\n/providers validate\n/status endpoints\n/route fix Kotlin compile error\n/help\n/exit\n' | java -jar "$JAR" 2>&1)"

printf '%s\n' "$OUT" | sed -n '1,260p'

echo "$OUT" | grep -qi "PROVIDER DESCRIPTORS"
echo "$OUT" | grep -qi "PROVIDER DESCRIPTORS: VALID"
echo "$OUT" | grep -qi "provider.groq.chat"
echo "$OUT" | grep -qi "cli.swarm_unbound"
echo "$OUT" | grep -qi "route:"
echo "$OUT" | grep -qiv '{"error"'
echo "$OUT" | grep -qiv "sk-"

echo "ATROPOS_CLI_SMOKE_OK"
