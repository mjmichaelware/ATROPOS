#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

# N005 is the bounded acceptance command for the calculator closure path. It
# composes existing static owners and reports runtime gates separately.
bash scripts/kotlin-compat-scan.sh
bash scripts/calculator-prerequisite-gate.sh
bash scripts/source-to-code-trace-gate-test.sh
bash scripts/hr-router-proof.sh
bash scripts/hierarchy-dispatch-proof.sh
bash scripts/governance-proof.sh
bash scripts/app-factory-wiring-proof.sh
bash scripts/app-factory-production-proof.sh
bash scripts/endpoint-manifest-proof.sh
bash -n scripts/app-factory-source-proof.sh scripts/app-factory-policy-proof.sh scripts/app-factory-nl-routing-proof.sh
git diff --check

printf '%s\n' \
  'N005_FINAL_ACCEPTANCE_COMMAND_OK' \
  "root=$ROOT" \
  'static_gates=pass' \
  'focused_runtime_tests=recorded_separately' \
  'full_build=not_run' \
  'jar_install_restart=not_run'
