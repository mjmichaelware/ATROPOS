#!/usr/bin/env bash
set -euo pipefail

cat >&2 <<'MESSAGE'
UNSUPPORTED: direct shell JAR swaps are disabled.
Use the installed ATROPOS self-host promotion path so VerifiedCompletionGate,
SelfHostSafetyHardFailGate, Director advisory, and SafeJarSwapGate remain the
single promotion chain.
MESSAGE
exit 78
