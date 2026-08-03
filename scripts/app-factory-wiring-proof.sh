#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

require() {
  local pattern="$1"
  shift
  rg -q --fixed-strings "$pattern" "$@" || {
    echo "APP_FACTORY_WIRING_MISSING pattern=$pattern" >&2
    exit 1
  }
}

# Verify the production call chain without launching a stale or mismatched JAR.
require '"/factory" -> factoryCommand.execute' src/main/kotlin/atropos/cli/CommandRouter.kt
require 'renderRun(prompt)' src/main/kotlin/atropos/cli/FactoryCommandHandler.kt src/main/kotlin/atropos/cli/ui/AppFactoryPlanRenderer.kt
require 'router.runLocal(prompt)' src/main/kotlin/atropos/cli/ui/AppFactoryPlanRenderer.kt
require 'planningGraph.planFromTexts' src/main/kotlin/atropos/core/factory/AppFactoryRouter.kt
require 'AppProjectGenerator(repoRoot).generateApp' src/main/kotlin/atropos/core/factory/AppFactoryRouter.kt
require 'projectRegistry.register' src/main/kotlin/atropos/core/factory/AppFactoryRouter.kt
require 'name = base.projectSpec.intent.name' src/main/kotlin/atropos/core/factory/AppFactoryRouter.kt
require 'mutationGate.requireAllowed' src/main/kotlin/atropos/core/factory/AppProjectGenerator.kt
require 'GitWorktreeOperation.INIT' src/main/kotlin/atropos/core/factory/AppProjectGenerator.kt
require 'EvidenceManifest' src/main/kotlin/atropos/core/factory/AppProjectGenerator.kt
require 'listOf("/factory", "run")' src/main/kotlin/atropos/cli/commands/SelfHostNaturalLanguageRouter.kt

if rg -n -i 'CalculatorProjectGenerator|calculator-specific|calculator intent' src/main/kotlin/atropos/core/factory src/main/kotlin/atropos/cli >/dev/null; then
  echo 'APP_FACTORY_WIRING_CALCULATOR_SPECIAL_CASE' >&2
  exit 1
fi

printf '%s\n' \
  'APP_FACTORY_WIRING_PROOF_OK' \
  "root=$ROOT" \
  'installed_runtime=not_run_stale_fingerprint'
