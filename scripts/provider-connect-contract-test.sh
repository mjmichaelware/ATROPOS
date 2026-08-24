#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SOURCE="$ROOT/src/main/kotlin/atropos/cli/ProviderCommandHandler.kt"

test -f "$SOURCE"
grep -Fq 'TerminalModeManager' "$SOURCE"
grep -Fq 'if (!terminal.enableRawMode()) return null' "$SOURCE"
grep -Fq 'finally {' "$SOURCE"
grep -Fq 'terminal.close()' "$SOURCE"

# A console-less fallback must not regress to line input, which echoes secrets.
if grep -Fq 'System.`in`.bufferedReader().readLine()' "$SOURCE"; then
  echo 'unsafe provider-connect line reader found' >&2
  exit 1
fi

printf '%s\n' 'ATROPOS_PROVIDER_CONNECT_CONTRACT_OK'
