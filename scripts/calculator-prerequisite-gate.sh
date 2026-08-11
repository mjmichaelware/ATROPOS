#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

require_file() {
  test -f "$1" || { echo "CALCULATOR_PREREQUISITE_MISSING $1" >&2; exit 1; }
}

require_test_contract() {
  local file="$1"
  rg -q -- '@Test' "$file" || {
    echo "CALCULATOR_PREREQUISITE_TEST_CONTRACT_MISSING $file" >&2
    exit 1
  }
}

require_pattern() {
  local pattern="$1"
  local file="$2"
  local label="$3"
  rg -q -- "$pattern" "$file" || {
    echo "CALCULATOR_PREREQUISITE_CONTRACT_MISSING $label $file" >&2
    exit 1
  }
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
hierarchy_tests=(
  src/test/kotlin/atropos/core/hr/HrRouterServiceTest.kt
  src/test/kotlin/atropos/core/director/DirectorServiceTest.kt
  src/test/kotlin/atropos/core/hierarchy/HierarchyRegistryTest.kt
  src/test/kotlin/atropos/core/auditor/AuditorServiceTest.kt
)
factory_surfaces=(
  src/main/kotlin/atropos/core/factory/AppActionRegistry.kt
  src/main/kotlin/atropos/core/factory/AppIntent.kt
  src/main/kotlin/atropos/core/factory/AppProjectSpec.kt
  src/main/kotlin/atropos/core/factory/AppProjectSpecParser.kt
  src/main/kotlin/atropos/core/factory/RepoScaffold.kt
  src/main/kotlin/atropos/core/factory/EvidenceManifest.kt
  src/main/kotlin/atropos/core/factory/AppProjectMutationGate.kt
  src/main/kotlin/atropos/core/factory/FactoryHierarchyGate.kt
  src/main/kotlin/atropos/core/hierarchy/HierarchyRegistry.kt
  src/main/kotlin/atropos/core/factory/AppProjectGenerator.kt
  src/main/kotlin/atropos/core/factory/AppFactoryRouter.kt
  src/main/kotlin/atropos/core/factory/FactoryLineage.kt
  src/main/kotlin/atropos/core/factory/FactoryResearchService.kt
  src/main/kotlin/atropos/core/provider/ContextEnvelopeFactory.kt
  src/main/kotlin/atropos/core/planning/InternalPlanningGraphService.kt
  src/main/kotlin/atropos/core/director/DirectorDagSupervisor.kt
  src/main/kotlin/atropos/core/director/DirectorDagSupervision.kt
  src/main/kotlin/atropos/core/agent/WorkerCodeProposalService.kt
  src/main/kotlin/atropos/cli/commands/AgentWorkerCommandHandler.kt
  src/main/kotlin/atropos/core/worktree/BoundedGitWorktreeCommandRunner.kt
  src/main/kotlin/atropos/cli/ui/AppFactoryPlanRenderer.kt
  src/test/kotlin/atropos/core/factory/AppProjectGeneratorTest.kt
  src/test/kotlin/atropos/core/factory/AppFactoryRouterTest.kt
  src/test/kotlin/atropos/core/worktree/BoundedGitWorktreeCommandRunnerTest.kt
  src/test/kotlin/atropos/cli/ui/AppFactoryPlanRendererTest.kt
  src/main/kotlin/atropos/core/artifact/ArtifactPipeline.kt
  src/main/kotlin/atropos/cli/commands/SelfHostNaturalLanguageRouter.kt
  src/test/kotlin/atropos/cli/commands/SelfHostNaturalLanguageRouterTest.kt
)

for file in "${provider_tests[@]}" "${terminal_tests[@]}" "${source_tests[@]}" "${endpoint_tests[@]}" "${hierarchy_tests[@]}" "${factory_surfaces[@]}"; do
  require_file "$file"
done

acceptance_test_files=(
  "${provider_tests[@]}"
  "${terminal_tests[@]}"
  "${source_tests[@]}"
  "${endpoint_tests[@]}"
  "${hierarchy_tests[@]}"
  src/test/kotlin/atropos/core/factory/AppProjectGeneratorTest.kt
  src/test/kotlin/atropos/core/factory/AppFactoryRouterTest.kt
  src/test/kotlin/atropos/core/worktree/BoundedGitWorktreeCommandRunnerTest.kt
  src/test/kotlin/atropos/cli/ui/AppFactoryPlanRendererTest.kt
  src/test/kotlin/atropos/cli/commands/SelfHostNaturalLanguageRouterTest.kt
)
for file in "${acceptance_test_files[@]}"; do
  require_test_contract "$file"
done

# N001-N005 are acceptance surfaces, not a second test runner. These bounded
# source checks ensure the canonical owners still expose the required matrix
# and proof contracts before an operator elects to run them.
require_pattern 'fun runAll\(' src/main/kotlin/atropos/core/provider/ProviderFixtureMatrixService.kt N001_provider_matrix
require_pattern 'REQUIRED_NORMALIZED_FIXTURES' src/main/kotlin/atropos/core/provider/ProviderFixtureMatrixService.kt N001_failure_matrix
require_pattern 'dryRun = true' src/main/kotlin/atropos/core/provider/ProviderFixtureMatrixService.kt N001_dry_run
require_pattern 'runRedactionFixture' src/main/kotlin/atropos/core/provider/ProviderFixtureMatrixService.kt N001_redaction
require_pattern 'TERM=dumb' src/main/kotlin/atropos/cli/config/ConfigurationManager.kt N002_dumb_terminal
require_pattern 'NO_COLOR' src/main/kotlin/atropos/cli/config/ConfigurationManager.kt N002_no_color
require_pattern 'ATROPOS_Source_Doc_1.txt' docs/authority/AUTHORITY_MANIFEST.tsv N003_authority_manifest
require_pattern 'source-to-code-trace-gate' scripts/calculator-final-acceptance.sh N003_trace_gate
require_pattern 'ENDPOINT_MANIFEST_PROOF_OK' scripts/endpoint-manifest-proof.sh N004_endpoint_proof
require_pattern 'manifest' src/main/kotlin/atropos/core/endpoint/OperationEndpoint.kt N004_endpoint_manifest
require_pattern 'N005_FINAL_ACCEPTANCE_COMMAND_OK' scripts/calculator-final-acceptance.sh N005_final_marker

if rg -n -i 'CalculatorProjectGenerator|calculator-specific|calculator intent' src/main/kotlin/atropos/core/factory src/main/kotlin/atropos/cli >/dev/null; then
  echo 'CALCULATOR_PRODUCT_SPECIAL_CASE_PRESENT' >&2
  exit 1
fi

rg -q 'generateApp\(prompt: String, projectId: String\)' src/main/kotlin/atropos/core/factory/AppProjectGenerator.kt || {
  echo 'GENERAL_APP_GENERATION_API_MISSING' >&2
  exit 1
}

rg -q 'kotlinc .*include-runtime' src/main/kotlin/atropos/core/factory/RepoScaffold.kt || {
  echo 'GENERATED_TEST_EXECUTION_MISSING' >&2
  exit 1
}

rg -q 'MainTestKt' src/main/kotlin/atropos/core/factory/RepoScaffold.kt || {
  echo 'GENERATED_TEST_ENTRYPOINT_MISSING' >&2
  exit 1
}

rg -q '\.atropos/generated-projects' src/main/kotlin/atropos/core/factory/AppProjectGenerator.kt src/main/kotlin/atropos/core/factory/AppProjectMutationGate.kt || {
  echo 'POLICY_COMPATIBLE_GENERATED_TERRITORY_MISSING' >&2
  exit 1
}

if rg -n 'build/generated-projects' src/main/kotlin/atropos/core/factory src/test/kotlin/atropos/core/factory scripts/app-factory-source-proof.sh >/dev/null; then
  echo 'FORBIDDEN_BUILD_GENERATED_TERRITORY_PRESENT' >&2
  exit 1
fi

require_file src/main/kotlin/atropos/core/endpoint/OperationRegistry.kt
require_file src/main/kotlin/atropos/core/endpoint/EndpointKind.kt
require_file src/main/kotlin/atropos/core/verification/VerifiedCompletionGate.kt
require_file scripts/app-factory-policy-proof.sh
require_file scripts/app-factory-nl-routing-proof.sh
require_file scripts/calculator-final-acceptance.sh
require_file scripts/hr-router-proof.sh
require_file scripts/hierarchy-dispatch-proof.sh
require_file scripts/governance-proof.sh
require_file scripts/app-factory-wiring-proof.sh
require_file scripts/app-factory-production-proof.sh
require_file scripts/app-factory-installed-proof.sh
require_file scripts/endpoint-manifest-proof.sh

printf '%s\n' \
  'CALCULATOR_PREREQUISITE_SURFACE_OK' \
  "root=$ROOT" \
  'provider_tests=present' \
  'terminal_tests=present' \
  'source_authority_tests=present' \
  'endpoint_parity_tests=present' \
  'acceptance_contracts=present' \
  'hierarchy_gate_tests=present' \
  'acceptance_test_contracts=present' \
  'general_app_factory_surfaces=present' \
  'test_execution=not_run'
