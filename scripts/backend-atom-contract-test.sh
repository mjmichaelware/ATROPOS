#!/usr/bin/env bash
set -euo pipefail

# Source-wiring gate for the backend atoms that are in this engine lane. This
# intentionally proves ownership and caller edges only; hosted Kotlin tests
# remain the authority for executable behavior.
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

file() { test -s "$ROOT/$1" || { echo "BACKEND_ATOM_CONTRACT_FAIL missing $1" >&2; exit 1; }; }
text() { rg -Fq -- "$2" "$ROOT/$1" || { echo "BACKEND_ATOM_CONTRACT_FAIL missing '$2' in $1" >&2; exit 1; }; }

# Install, onboarding, routing, and the single autonomous hierarchy owner.
file install.sh
file src/main/kotlin/atropos/core/provider/ProviderOnboarding.kt
file src/main/kotlin/atropos/core/provider/ProviderDescriptor.kt
file src/main/kotlin/atropos/core/provider/RoutePolicy.kt
file src/main/kotlin/atropos/cli/ProviderCommandHandler.kt
file src/main/kotlin/atropos/core/autonomous/ProviderWorkerDirector.kt
text src/main/kotlin/atropos/core/autonomous/AutonomousOrchestrator.kt "ProviderWorkerDirector"
text src/main/kotlin/atropos/cli/CommandRouter.kt '"/providers"'

# One bridge owner for the declared local HTTP/SSE surface.
file src/main/kotlin/atropos/bridge/http/EngineHttpServer.kt
file src/main/kotlin/atropos/bridge/BridgeRoutes.kt
for route in '/v1/session' '/v1/message' '/v1/events' '/v1/status' '/v1/approve' '/v1/evidence' '/v1/files' '/v1/cli'; do
  text src/main/kotlin/atropos/bridge/BridgeRoutes.kt "$route"
done

# One MCP host, with bounded process/policy/evidence and the existing callers.
file src/main/kotlin/atropos/core/integration/McpHostManager.kt
file src/main/kotlin/atropos/cli/McpCommandHandler.kt
file src/main/kotlin/atropos/bridge/BridgeMcpHandler.kt
text src/main/kotlin/atropos/core/integration/McpHostManager.kt 'health.tsv'
text src/main/kotlin/atropos/core/integration/McpHostManager.kt 'TypedToolExecutor'
text src/main/kotlin/atropos/core/integration/McpHostManager.kt 'BoundedProcessRunner'
text src/main/kotlin/atropos/core/integration/McpHostManager.kt 'RedactionFilter'

# Tier-0 integrations compose onto existing gates and secret/evidence owners.
file src/main/kotlin/atropos/core/integration/IntegrationRegistry.kt
file src/main/kotlin/atropos/core/sentry/SentryApiClient.kt
file src/main/kotlin/atropos/core/sentry/SentryRepairCoordinator.kt
file src/main/kotlin/atropos/core/github/GitHubApiClient.kt
file src/main/kotlin/atropos/core/github/GitHubBinding.kt
file src/main/kotlin/atropos/cli/shell/ShellCommandRunner.kt
text src/main/kotlin/atropos/core/sentry/SentryApiClient.kt 'IntegrationRegistry'
text src/main/kotlin/atropos/core/github/GitHubApiClient.kt 'SecretSinkMatrix'
text src/main/kotlin/atropos/core/github/GitHubApiClient.kt 'BoundedAgencyGate'
text src/main/kotlin/atropos/cli/shell/ShellCommandRunner.kt 'BoundedAgencyGate'

# Factory resume/repair and the single obligation loop remain production-wired.
file src/main/kotlin/atropos/cli/FactoryCommandHandler.kt
file src/main/kotlin/atropos/core/factory/AppFactoryRouter.kt
file src/main/kotlin/atropos/core/factory/FactoryRunHandoff.kt
file src/main/kotlin/atropos/core/factory/FactoryObligationLoop.kt
text src/main/kotlin/atropos/cli/FactoryCommandHandler.kt 'resume'
text src/main/kotlin/atropos/core/factory/AppFactoryRouter.kt 'FactoryRunHandoff'
text src/main/kotlin/atropos/core/factory/FactoryObligationLoop.kt 'executeUntilSettled'

# Both hosted entrypoints must run this contract.
text .github/workflows/compile-gate.yml 'backend-atom-contract-test.sh'
text scripts/atropos-verify-worktree.sh 'backend-atom-contract-test.sh'

printf '%s\n' 'ATROPOS_BACKEND_ATOM_CONTRACT_OK'
