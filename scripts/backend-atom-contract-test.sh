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
file src/main/kotlin/atropos/core/provider/SourceBindingFetcher.kt
file src/main/kotlin/atropos/cli/ProviderCommandHandler.kt
file src/main/kotlin/atropos/core/autonomous/ProviderWorkerDirector.kt
text src/main/kotlin/atropos/core/autonomous/AutonomousOrchestrator.kt "ProviderWorkerDirector"
text src/main/kotlin/atropos/cli/CommandRouter.kt '"/providers"'
text src/main/kotlin/atropos/core/provider/SourceBindingFetcher.kt 'localOnly'
text src/main/kotlin/atropos/core/provider/SourceBindingFetcher.kt 'disabled by localOnly'
text src/main/kotlin/atropos/core/provider/ProviderOnboarding.kt 'genericProviderIds'
text src/main/kotlin/atropos/core/provider/SourceBindingFetcher.kt 'connectTimeout(java.time.Duration.ofSeconds(5))'
text src/main/kotlin/atropos/core/provider/SourceBindingFetcher.kt 'SecretSinkMatrix.isEgressPermitted'
text src/main/kotlin/atropos/core/provider/SourceBindingFetcher.kt 'HttpClient.Redirect.NEVER'
text src/main/kotlin/atropos/core/github/GitHubDeviceAuthClient.kt 'GitHub OAuth disabled by local-only mode'
text src/main/kotlin/atropos/core/github/GitHubDeviceAuthClient.kt 'requireJsonObject(response.body)'

# One bridge owner for the declared local HTTP/SSE surface.
file src/main/kotlin/atropos/bridge/http/EngineHttpServer.kt
file src/main/kotlin/atropos/bridge/BridgeRoutes.kt
for route in '/v1/session' '/v1/message' '/v1/events' '/v1/status' '/v1/approve' '/v1/evidence' '/v1/files' '/v1/cli'; do
  text src/main/kotlin/atropos/bridge/BridgeRoutes.kt "$route"
done

# One MCP host, with bounded process/policy/evidence and the existing callers.
file src/main/kotlin/atropos/core/integration/McpHostManager.kt
file src/main/kotlin/atropos/core/integration/McpConfigParser.kt
file src/main/kotlin/atropos/cli/McpCommandHandler.kt
file src/main/kotlin/atropos/bridge/BridgeMcpHandler.kt
text src/main/kotlin/atropos/core/integration/McpHostManager.kt 'McpConfigParser.parse'
text src/main/kotlin/atropos/core/integration/McpHostManager.kt 'health.tsv'
text src/main/kotlin/atropos/core/integration/McpHostManager.kt 'TypedToolExecutor'
text src/main/kotlin/atropos/core/integration/McpHostManager.kt 'BoundedProcessRunner'
text src/main/kotlin/atropos/core/integration/McpHostManager.kt 'RedactionFilter'
text src/main/kotlin/atropos/core/integration/McpHostManager.kt 'SecretSinkMatrix.isEgressPermitted'
text src/main/kotlin/atropos/core/integration/McpHostManager.kt 'McpConfigParser.requireJsonObject'
text src/main/kotlin/atropos/core/integration/McpHostManager.kt 'HttpClient.Redirect.NEVER'
text src/main/kotlin/atropos/core/integration/McpHostManager.kt 'readNBytes(DEFAULT_REMOTE_RESPONSE_BYTES + 1)'

# Tier-0 integrations compose onto existing gates and secret/evidence owners.
file src/main/kotlin/atropos/core/integration/IntegrationRegistry.kt
file src/main/kotlin/atropos/core/sentry/SentryApiClient.kt
file src/main/kotlin/atropos/core/sentry/SentryRepairCoordinator.kt
file src/main/kotlin/atropos/core/github/GitHubApiClient.kt
file src/main/kotlin/atropos/core/github/GitHubBinding.kt
file src/main/kotlin/atropos/cli/shell/ShellCommandRunner.kt
text src/main/kotlin/atropos/core/sentry/SentryApiClient.kt 'IntegrationRegistry'
text src/main/kotlin/atropos/core/sentry/SentryApiClient.kt 'isJsonObjectEnvelope'
text src/main/kotlin/atropos/core/sentry/SentryApiClient.kt 'listUnresolvedIssues'
text src/main/kotlin/atropos/core/github/GitHubApiClient.kt 'SecretSinkMatrix'
text src/main/kotlin/atropos/core/github/GitHubApiClient.kt 'BoundedAgencyGate'
text src/main/kotlin/atropos/core/github/GitHubApiClient.kt 'MAX_RESPONSE_CHARS'
text src/main/kotlin/atropos/core/ProviderHttpClient.kt 'MAX_RESPONSE_BYTES'
text src/main/kotlin/atropos/core/Provider.kt 'MAX_RESPONSE_BYTES'
text src/main/kotlin/atropos/core/provider/adapter/NonOpenAiFreeKernelAdapter.kt 'readNBytes'
text src/main/kotlin/atropos/core/provider/adapter/DataInfraKernelAdapter.kt 'readNBytes'
text src/main/kotlin/atropos/core/provider/adapter/OpenAiCompatibleKernelAdapter.kt 'readNBytes'
text src/main/kotlin/atropos/core/SelfUpdate.kt 'SecretSinkMatrix.isEgressPermitted'
text src/main/kotlin/atropos/core/SelfUpdate.kt 'HttpClient.Redirect.NEVER'
text src/main/kotlin/atropos/core/SelfUpdate.kt 'MAX_DOWNLOAD_BYTES'
text src/main/kotlin/atropos/core/verification/GitHubActionsCompileRunner.kt 'SecretSinkMatrix.isEgressPermitted'
text src/main/kotlin/atropos/core/verification/GitHubActionsCompileRunner.kt 'HttpClient.Redirect.NEVER'
text src/main/kotlin/atropos/core/verification/GitHubActionsCompileRunner.kt 'MAX_RESPONSE_CHARS'
text src/main/kotlin/atropos/core/verification/GitHubActionsCompileRunner.kt 'readNBytes(MAX_RESPONSE_CHARS + 1)'
text src/main/kotlin/atropos/core/sentry/SentryApiClient.kt 'HttpClient.Redirect.NEVER'
text src/main/kotlin/atropos/core/sentry/SentryApiClient.kt 'readNBytes(MAX_RESPONSE_BYTES + 1)'
text src/main/kotlin/atropos/cli/SentryCommandHandler.kt 'redactionFilter.compact'
text src/main/kotlin/atropos/cli/McpCommandHandler.kt 'redactionFilter.compact'
text src/main/kotlin/atropos/cli/ProviderCommandHandler.kt 'redactionFilter.compact'
text src/main/kotlin/atropos/cli/AuthCommandHandler.kt 'redactionFilter.compact'
text src/main/kotlin/atropos/cli/ui/StatusAuthRenderer.kt 'redactionFilter.redact(resolution.value)'
text src/main/kotlin/atropos/cli/PaidCommandHandler.kt 'redactionFilter.compact'
text src/main/kotlin/atropos/cli/SideConversationService.kt 'redactionFilter.compact'
text src/main/kotlin/atropos/core/github/GitHubApiClient.kt 'readNBytes(MAX_RESPONSE_CHARS + 1)'
text src/main/kotlin/atropos/core/github/GitHubDeviceAuthClient.kt 'HttpClient.Redirect.NEVER'
text src/main/kotlin/atropos/core/ProviderState.kt 'readNBytes(MAX_RESPONSE_BYTES + 1)'
text src/main/kotlin/atropos/core/agent/ProviderSessionSupervisor.kt 'HttpClient.Redirect.NEVER'
text src/main/kotlin/atropos/core/provider/adapter/BedrockKernelAdapter.kt 'readNBytes(MAX_RESPONSE_BYTES + 1)'
text src/main/kotlin/atropos/core/platform/PlatformAbstraction.kt 'requestMethod = "HEAD"'
text src/main/kotlin/atropos/core/platform/PlatformAbstraction.kt 'runtime.localOnly'
text src/main/kotlin/atropos/core/provider/QuotaLedger.kt 'StandardCopyOption.ATOMIC_MOVE'
text src/main/kotlin/atropos/core/provider/QuotaLedger.kt 'Files.createTempFile'
text src/main/kotlin/atropos/core/provider/QuotaLedger.kt 'atomicWrite(restored'
text src/main/kotlin/atropos/cli/shell/ShellCommandRunner.kt 'BoundedAgencyGate'
text src/main/kotlin/atropos/cli/ui/StatusQuotaRenderer.kt 'redactionFilter.redact(item.reason)'
text src/main/kotlin/atropos/core/provider/adapter/AdapterRouteFacade.kt 'redactionFilter.redact(it.reason)'
text src/main/kotlin/atropos/cli/ui/AnsiTerminalEngine.kt 'val safeLines = lines.map(redactionFilter::redact)'
text src/main/kotlin/atropos/cli/ui/AnsiTerminalEngine.kt 'val safeMessage = redactionFilter.redact(message)'
text src/main/kotlin/atropos/bridge/BridgeFilesHandler.kt 'StandardCopyOption.ATOMIC_MOVE'
text src/main/kotlin/atropos/bridge/BridgeFilesHandler.kt 'Files.size(it) <= MAX_UPLOAD_BYTES'
text src/main/kotlin/atropos/bridge/BridgeCommandHandler.kt 'redactionFilter.compact(failure.message.orEmpty())'
text src/main/kotlin/atropos/bridge/BridgeEvidenceHandler.kt 'redactionFilter.compact(e.message ?: e.javaClass.simpleName)'
text src/main/kotlin/atropos/bridge/http/EngineHttpServer.kt 'lastErrorRef.set(redactionFilter.compact(e.message ?: "request failed"))'
text src/main/kotlin/atropos/bridge/BridgeQueueHandler.kt 'redactionFilter.redact(entry.task)'
text src/main/kotlin/atropos/bridge/conversation/QueuedWorkConversationResponder.kt 'redactionFilter.compact('
text src/main/kotlin/atropos/bridge/projection/RecoveryProjection.kt 'redactionFilter.redact(report?.message.orEmpty())'
text src/main/kotlin/atropos/bridge/projection/ActivityProjection.kt 'redactionFilter.redact(event.detail)'
text src/main/kotlin/atropos/bridge/projection/AuthorityProjection.kt 'redactionFilter.redact(violation.reason)'
text src/main/kotlin/atropos/bridge/projection/ExportProjection.kt 'redactionFilter.redact(resolution.reason)'
text src/main/kotlin/atropos/bridge/projection/ThinkingProjection.kt 'redactionFilter.redact(line.text)'
text src/main/kotlin/atropos/core/integration/MarkItDownIngestService.kt 'MAX_SOURCE_BYTES'
text src/main/kotlin/atropos/core/integration/MarkItDownIngestService.kt 'Files.isSymbolicLink(source)'
text src/main/kotlin/atropos/core/integration/MarkItDownIngestService.kt 'StandardCopyOption.ATOMIC_MOVE'

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
