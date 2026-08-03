#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

require_file() {
  test -f "$1" || { echo "CALCULATOR_PREREQUISITE_MISSING $1" >&2; exit 1; }
}

require_file docs/source/ATROPOS_Source_Doc_1.txt
require_file docs/source/ATROPOS_Source_Doc_2.txt
require_file docs/source/ATROPOS_Source_Doc_3.txt
require_file docs/completion/ATROPOS_SD1_SD2_SPECGRAPH_ATOM_DAG.md
require_file docs/authority/AUTHORITY_MANIFEST.tsv

# These are characterization surfaces for the existing canonical owners. This
# gate inventories them; it intentionally does not launch Gradle.
provider_tests=(
  src/test/kotlin/atropos/core/provider/ProviderActivationServiceTest.kt
  src/test/kotlin/atropos/core/provider/ProviderCascadeOrderTest.kt
  src/test/kotlin/atropos/core/provider/ProviderFixtureMatrixServiceTest.kt
  src/test/kotlin/atropos/core/provider/QuotaLedgerRouteTruthTest.kt
)
terminal_tests=(
  src/test/kotlin/atropos/cli/ui/AnsiTerminalEngineHelpTest.kt
  src/test/kotlin/atropos/cli/CommandRouterHelpTest.kt
)
source_tests=(
  src/test/kotlin/atropos/dloi/DloiServiceTest.kt
  src/test/kotlin/atropos/dloi/HigZeroGuardContractTest.kt
  src/test/kotlin/atropos/core/provider/SourceBindingContextPackerTest.kt
)
endpoint_tests=(
  src/test/kotlin/atropos/cli/CommandRouterIdentityTest.kt
  src/test/kotlin/atropos/cli/CommandRouterHelpTest.kt
)

for file in "${provider_tests[@]}" "${terminal_tests[@]}" "${source_tests[@]}" "${endpoint_tests[@]}"; do
  require_file "$file"
done

require_file src/main/kotlin/atropos/core/endpoint/OperationRegistry.kt
require_file src/main/kotlin/atropos/core/verification/VerifiedCompletionGate.kt

printf '%s\n' \
  'CALCULATOR_PREREQUISITE_SURFACE_OK' \
  "root=$ROOT" \
  'provider_tests=present' \
  'terminal_tests=present' \
  'source_authority_tests=present' \
  'endpoint_parity_tests=present' \
  'test_execution=not_run'
