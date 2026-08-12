#!/usr/bin/env bash
# N005 final acceptance test wrapper
set -euo pipefail
SCRIPT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
bash "$SCRIPT_ROOT/calculator-final-acceptance.sh"
