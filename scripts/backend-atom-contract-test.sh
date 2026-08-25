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
text install.sh 'invalid repository; expected owner/name'
text install.sh 'invalid version; expected latest or a v-prefixed release tag'
text install.sh 'if [ -n "$HOST_PREFIX" ]; then'
text install.sh 'DOWNLOAD='
text install.sh '$DOWNLOAD "$TMP" "$JAR_URL"'
text install.sh 'export ATROPOS_PLATFORM='
text install.sh 'chmod +x "$BIN_DIR/atropos"'
file src/main/kotlin/atropos/core/Config.kt
text src/main/kotlin/atropos/core/Config.kt 'ATROPOS_CONFIG_DIR'
text install.sh 'export ATROPOS_CONFIG_DIR='
text install.sh 'CONFIG_DIR="${ATROPOS_CONFIG_DIR:-${ATROPOS_PREFIX:-$HOME/.atropos}}"'
file src/main/kotlin/atropos/core/provider/ProviderOnboarding.kt
file src/main/kotlin/atropos/core/provider/ProviderDescriptor.kt
file src/main/kotlin/atropos/core/provider/RoutePolicy.kt
file src/main/kotlin/atropos/core/provider/SourceBindingFetcher.kt
file src/main/kotlin/atropos/cli/ProviderCommandHandler.kt
file src/main/kotlin/atropos/core/autonomous/ProviderWorkerDirector.kt
text src/main/kotlin/atropos/core/autonomous/AutonomousOrchestrator.kt "ProviderWorkerDirector"
text src/main/kotlin/atropos/cli/CommandRouter.kt '"/providers"'
text src/main/kotlin/atropos/cli/ProviderCommandHandler.kt '"test" -> renderLiveTest(tokens)'
text src/main/kotlin/atropos/cli/ProviderCommandHandler.kt 'liveTestHealthReporter'
text src/main/kotlin/atropos/cli/input/CommandCatalog.kt '"/providers test <id>"'
text src/main/kotlin/atropos/cli/input/CommandCatalog.kt '"/providers enable"'
text src/main/kotlin/atropos/cli/ProviderCommandHandler.kt '"enable" -> renderEnable(onboarding, tokens)'
text src/main/kotlin/atropos/core/provider/ProviderOnboarding.kt 'fun enable(providerId: String)'
text src/main/kotlin/atropos/core/provider/ProviderOnboarding.kt 'fun recordLiveTest(providerId: String, healthy: Boolean)'
text src/main/kotlin/atropos/core/provider/adapter/AnthropicKernelAdapter.kt 'x-api-key'
text src/main/kotlin/atropos/core/provider/adapter/BedrockKernelAdapter.kt 'AwsSigV4'
text src/main/kotlin/atropos/core/provider/adapter/OpenAiCompatibleProviderCatalog.kt 'providerId = "deepseek_direct"'
text src/main/kotlin/atropos/core/provider/adapter/OpenAiCompatibleProviderCatalog.kt 'providerId = "mistral"'
text src/main/kotlin/atropos/core/provider/ProviderOnboarding.kt 'renderLaunchSummary'
text src/main/kotlin/atropos/core/provider/ProviderOnboarding.kt 'ProviderCascadeOrder.order(candidateIds, registry)'
text src/main/kotlin/atropos/core/provider/ProviderOnboarding.kt 'no healthy providers; set one key'
text src/main/kotlin/atropos/core/provider/ProviderOnboarding.kt 'export GROQ_API_KEY='
text src/main/kotlin/atropos/core/provider/RoutePolicy.kt 'candidate.id !in healthyProviderIds.invoke()'
file src/main/kotlin/atropos/core/policy/ExecutionPolicyEngine.kt
file src/main/kotlin/atropos/core/paid/EmergencyPaidGate.kt
text src/main/kotlin/atropos/core/policy/ExecutionPolicyEngine.kt 'if (request.paidProvider)'
text src/main/kotlin/atropos/core/policy/ExecutionPolicyEngine.kt 'paidGate.isProviderUnlocked(provider)'
text src/main/kotlin/atropos/core/policy/ExecutionPolicyEngine.kt 'paid provider remains locked'
text src/main/kotlin/atropos/core/provider/ProviderCascadeRouter.kt 'paidApprovalAfterFreeExhaustion'
text src/main/kotlin/atropos/core/provider/ProviderCascadeRouter.kt 'FREE/LOCAL cascade exhausted'
text src/main/kotlin/atropos/Main.kt 'ProviderOnboardingService().renderLaunchSummary()'
text src/main/kotlin/atropos/core/provider/SourceBindingFetcher.kt 'localOnly'
text src/main/kotlin/atropos/core/provider/SourceBindingFetcher.kt 'disabled by localOnly'
text src/main/kotlin/atropos/core/provider/ProviderOnboarding.kt 'genericProviderIds'
text src/main/kotlin/atropos/core/provider/adapter/AdapterRegistry.kt 'ATROPOS_PROVIDER_$canonical'
text src/main/kotlin/atropos/core/provider/ProviderOnboarding.kt 'fun preferredProviderIds()'
text src/main/kotlin/atropos/core/provider/ProviderOnboarding.kt 'fun disable(providerId: String)'
text src/main/kotlin/atropos/core/provider/adapter/AdapterRouteFacade.kt 'preferredProviderIds = { atropos.core.provider.ProviderOnboardingService().preferredProviderIds() }'
text src/main/kotlin/atropos/core/provider/RoutePolicy.kt 'preferredProviderIds?.invoke()?.indexOf(providerId)'
text src/main/kotlin/atropos/core/provider/SourceBindingFetcher.kt 'connectTimeout(java.time.Duration.ofSeconds(5))'
text src/main/kotlin/atropos/core/provider/SourceBindingFetcher.kt 'SecretSinkMatrix.isEgressPermitted'
text src/main/kotlin/atropos/core/provider/SourceBindingFetcher.kt 'HttpClient.Redirect.NEVER'
text src/main/kotlin/atropos/core/github/GitHubDeviceAuthClient.kt 'GitHub OAuth disabled by local-only mode'
text src/main/kotlin/atropos/core/github/GitHubDeviceAuthClient.kt 'requireJsonObject(response.body)'
text .github/actions/atropos-verify/action.yml 'verify-script must stay inside working-directory'
text .github/actions/atropos-verify/action.yml 'test -f "$VERIFY_SCRIPT"'

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
file src/main/kotlin/atropos/cli/input/CommandCatalog.kt
text src/main/kotlin/atropos/core/integration/McpHostManager.kt 'McpConfigParser.parse'
text src/main/kotlin/atropos/core/integration/McpHostManager.kt 'fun search(query: String)'
text src/main/kotlin/atropos/core/integration/McpHostManager.kt 'MCP search refused by territory bridge'
text src/main/kotlin/atropos/core/integration/McpHostManager.kt 'isMemoryAuthorityMutation'
text src/main/kotlin/atropos/core/integration/McpHostManager.kt 'health.tsv'
text src/main/kotlin/atropos/core/integration/McpHostManager.kt 'localOnly && server.remote'
text src/main/kotlin/atropos/core/integration/McpHostManager.kt 'persistHealth(statuses)'
text src/main/kotlin/atropos/core/integration/McpHostManager.kt 'server.name !in allowlist && allowlist.isNotEmpty()'
text src/main/kotlin/atropos/core/integration/McpHostManager.kt 'community server requires explicit allowlist'
text src/main/kotlin/atropos/core/integration/McpHostManager.kt 'MCP server is not allowlisted'
text src/main/kotlin/atropos/core/integration/McpHostManager.kt 'require(server.enabled)'
text src/main/kotlin/atropos/core/integration/McpHostManager.kt 'fun boundedTools'
text src/main/kotlin/atropos/core/integration/McpHostManager.kt 'take(budget.maxTools)'
text src/main/kotlin/atropos/core/integration/McpHostManager.kt 'description.take(budget.maxDescriptionChars)'
text src/main/kotlin/atropos/core/integration/McpHostManager.kt 'requireToolWithinBudget'
text src/main/kotlin/atropos/core/integration/McpHostManager.kt 'method\":\"initialize\"'
text src/main/kotlin/atropos/core/integration/McpHostManager.kt 'method\":\"tools/list\"'
text src/main/kotlin/atropos/core/integration/McpHostManager.kt 'notifications/initialized'
text src/main/kotlin/atropos/core/integration/McpHostManager.kt 'remoteExchange(server, "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}'
text src/main/kotlin/atropos/core/integration/McpHostManager.kt 'writer.write("{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}'
text src/test/kotlin/atropos/core/integration/McpHostManagerTest.kt 'assertEquals(3, requests.size)'
text src/test/kotlin/atropos/core/integration/McpHostManagerTest.kt 'requests[1].contains("notifications/initialized")'
text src/main/kotlin/atropos/core/integration/McpHostManager.kt 'processRunner.start(listOf(command) + server.args, root)'
text src/main/kotlin/atropos/core/integration/McpHostManager.kt 'if (server.remote)'
text src/main/kotlin/atropos/core/integration/McpHostManager.kt 'private fun postRemote'
text src/main/kotlin/atropos/core/integration/McpHostManager.kt 'HttpClient.Redirect.NEVER'
text src/main/kotlin/atropos/core/integration/McpHostManager.kt 'return McpToolCallResult(safeResponse, recordToolResult(serverName, toolName, safeResponse))'
text src/main/kotlin/atropos/core/integration/McpHostManager.kt 'fun recordToolResult'
text src/main/kotlin/atropos/core/integration/McpHostManager.kt 'tool result declared no durable evidence'
text src/main/kotlin/atropos/core/integration/McpHostManager.kt 'MessageDigest.getInstance("SHA-256")'
text src/main/kotlin/atropos/core/integration/McpHostManager.kt 'TypedToolExecutor'
text src/main/kotlin/atropos/core/integration/McpHostManager.kt 'BoundedProcessRunner'
text src/main/kotlin/atropos/core/integration/McpHostManager.kt 'process.destroyForcibly()'
text src/main/kotlin/atropos/core/integration/McpHostManager.kt 'process.waitFor(1, TimeUnit.SECONDS)'
text src/main/kotlin/atropos/core/integration/McpHostManager.kt 'RedactionFilter'
text src/main/kotlin/atropos/core/integration/McpHostManager.kt 'SecretSinkMatrix.isEgressPermitted'
text src/main/kotlin/atropos/core/integration/McpHostManager.kt 'McpConfigParser.requireJsonObject'
text src/main/kotlin/atropos/core/integration/McpHostManager.kt 'HttpClient.Redirect.NEVER'
text src/main/kotlin/atropos/core/integration/McpHostManager.kt 'readNBytes(DEFAULT_REMOTE_RESPONSE_BYTES + 1)'
text src/main/kotlin/atropos/bridge/BridgeMcpHandler.kt 'manager.callTool'
text src/main/kotlin/atropos/bridge/BridgeMcpHandler.kt 'evidenceSha256'
text src/main/kotlin/atropos/bridge/BridgeMcpHandler.kt 'noEvidenceReason'
text src/main/kotlin/atropos/bridge/BridgeMcpHandler.kt 'redactionFilter.compact'
text src/main/kotlin/atropos/bridge/BridgeRoutes.kt '"/v1/mcp/call"'
text src/main/kotlin/atropos/bridge/BridgeRoutes.kt '"/v1/mcp/status"'
text src/main/kotlin/atropos/cli/McpCommandHandler.kt 'null, "list" -> uiEngine.renderBlock(manager.statuses()'
text src/main/kotlin/atropos/cli/McpCommandHandler.kt '"test" -> uiEngine.renderBlock(manager.statuses()'
text src/main/kotlin/atropos/cli/McpCommandHandler.kt '"search" -> search(tokens)'
text src/main/kotlin/atropos/cli/input/CommandCatalog.kt '"/mcp list"'
text src/main/kotlin/atropos/cli/input/CommandCatalog.kt '"/mcp test"'
text src/main/kotlin/atropos/cli/input/CommandCatalog.kt '"/mcp search <query>"'

# Tier-0 integrations compose onto existing gates and secret/evidence owners.
file src/main/kotlin/atropos/core/integration/IntegrationRegistry.kt
text src/main/kotlin/atropos/core/integration/IntegrationRegistry.kt 'IntegrationDescriptor("filesystem", "stdio_or_http"'
text src/main/kotlin/atropos/core/integration/IntegrationRegistry.kt 'IntegrationDescriptor("git-local", "process"'
file src/main/kotlin/atropos/core/sentry/SentryApiClient.kt
file src/main/kotlin/atropos/core/sentry/SentryRepairCoordinator.kt
file src/main/kotlin/atropos/cli/SentryCommandHandler.kt
file src/main/kotlin/atropos/core/github/GitHubApiClient.kt
file src/main/kotlin/atropos/core/github/GitHubBinding.kt
file src/main/kotlin/atropos/cli/GitHubCommandHandler.kt
file src/main/kotlin/atropos/cli/shell/ShellCommandRunner.kt
file src/main/kotlin/atropos/cli/ShellCommandHandler.kt
text src/main/kotlin/atropos/core/sentry/SentryApiClient.kt 'IntegrationRegistry'
text src/main/kotlin/atropos/core/sentry/SentryApiClient.kt 'isJsonObjectEnvelope'
text src/main/kotlin/atropos/core/sentry/SentryApiClient.kt 'listUnresolvedIssues'
text src/main/kotlin/atropos/core/sentry/SentryRepairCoordinator.kt 'fun prepare'
text src/main/kotlin/atropos/core/sentry/SentryRepairCoordinator.kt 'TerritoryEnforcer'
text src/main/kotlin/atropos/core/sentry/SentryRepairCoordinator.kt 'workerProposals.propose'
text src/main/kotlin/atropos/core/sentry/SentryRepairCoordinator.kt 'evidenceStore.put'
text src/main/kotlin/atropos/cli/SentryCommandHandler.kt '"list", "inspect", "propose"'
text src/main/kotlin/atropos/cli/CommandRouter.kt 'SentryCommandHandler(uiEngine)'
text src/main/kotlin/atropos/cli/CommandRouter.kt '"/sentry" ->'
text src/main/kotlin/atropos/core/github/GitHubApiClient.kt 'SecretSinkMatrix'
text src/main/kotlin/atropos/core/github/GitHubApiClient.kt 'BoundedAgencyGate'
text src/main/kotlin/atropos/core/github/GitHubApiClient.kt 'MAX_RESPONSE_CHARS'
text src/main/kotlin/atropos/cli/GitHubCommandHandler.kt 'GitHubBinding'
text src/main/kotlin/atropos/cli/GitHubCommandHandler.kt 'create-issue'
text src/main/kotlin/atropos/cli/GitHubCommandHandler.kt '"issues" -> binding.listIssues'
text src/main/kotlin/atropos/cli/CommandRouter.kt 'GitHubCommandHandler(config, uiEngine)'
text src/main/kotlin/atropos/cli/CommandRouter.kt '"/github" ->'
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
file src/main/kotlin/atropos/cli/help/HelpGenerator.kt
file src/main/kotlin/atropos/cli/input/CommandRegistry.kt
file src/main/kotlin/atropos/bridge/BridgeRoutes.kt
text src/main/kotlin/atropos/cli/CommandRouter.kt 'renderHelpPage'
text src/main/kotlin/atropos/cli/help/HelpGenerator.kt 'RegistryHelpSource'
text src/main/kotlin/atropos/cli/input/CommandRegistry.kt 'helpSections()'
text src/main/kotlin/atropos/bridge/BridgeRoutes.kt '"/v1/commands"'
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
text src/main/kotlin/atropos/cli/shell/ShellCommandRunner.kt 'fun gitStatus()'
text src/main/kotlin/atropos/cli/shell/ShellCommandRunner.kt 'fun gitDiff()'
text src/main/kotlin/atropos/cli/shell/ShellCommandRunner.kt 'fun gitConflicts()'
text src/main/kotlin/atropos/cli/ShellCommandHandler.kt 'tokens.getOrNull(1)?.lowercase() == "status"'
text src/main/kotlin/atropos/cli/ShellCommandHandler.kt 'tokens.getOrNull(1)?.lowercase() == "diff"'
text src/main/kotlin/atropos/cli/ShellCommandHandler.kt 'tokens.getOrNull(1)?.lowercase() == "conflicts"'
text src/main/kotlin/atropos/cli/CommandRouter.kt 'shellCommand.git(tokens)'
text src/main/kotlin/atropos/cli/ui/StatusQuotaRenderer.kt 'redactionFilter.redact(item.reason)'
text src/main/kotlin/atropos/core/provider/adapter/AdapterRouteFacade.kt 'redactionFilter.redact(it.reason)'
text src/main/kotlin/atropos/cli/ui/AnsiTerminalEngine.kt 'val safeLines = lines.map(redactionFilter::redact)'
text src/main/kotlin/atropos/cli/ui/AnsiTerminalEngine.kt 'val safeMessage = redactionFilter.redact(message)'
text src/main/kotlin/atropos/bridge/BridgeFilesHandler.kt 'StandardCopyOption.ATOMIC_MOVE'
text src/main/kotlin/atropos/bridge/BridgeFilesHandler.kt 'Files.size(it) <= MAX_UPLOAD_BYTES'
text src/main/kotlin/atropos/bridge/BridgeCommandHandler.kt 'redactionFilter.compact(failure.message.orEmpty())'
text src/main/kotlin/atropos/bridge/BridgeEvidenceHandler.kt 'redactionFilter.compact(e.message ?: e.javaClass.simpleName)'
text src/main/kotlin/atropos/bridge/BridgeEvidenceHandler.kt 'redactionFilter.redact(evidencePathStr)'
text src/main/kotlin/atropos/bridge/BridgeEvidenceHandler.kt 'redactionFilter.redact(entry.evidence.orEmpty())'
text src/main/kotlin/atropos/bridge/http/EngineHttpServer.kt 'lastErrorRef.set(redactionFilter.compact(e.message ?: "request failed"))'
text src/main/kotlin/atropos/bridge/BridgeQueueHandler.kt 'redactionFilter.redact(entry.task)'
text src/main/kotlin/atropos/bridge/conversation/QueuedWorkConversationResponder.kt 'redactionFilter.compact('
text src/main/kotlin/atropos/bridge/projection/RecoveryProjection.kt 'redactionFilter.redact(report?.message.orEmpty())'
text src/main/kotlin/atropos/bridge/projection/ActivityProjection.kt 'redactionFilter.redact(event.detail)'
text src/main/kotlin/atropos/bridge/projection/AuthorityProjection.kt 'redactionFilter.redact(violation.reason)'
text src/main/kotlin/atropos/bridge/projection/ExportProjection.kt 'redactionFilter.redact(resolution.reason)'
text src/main/kotlin/atropos/bridge/projection/ThinkingProjection.kt 'redactionFilter.redact(line.text)'
text src/main/kotlin/atropos/bridge/BridgeSessionHandler.kt 'redactionFilter.redact(session.title)'
text src/main/kotlin/atropos/bridge/projection/CheckpointProjection.kt 'redactionFilter.redact(summary.goalId)'
text src/main/kotlin/atropos/bridge/BridgeEventsHandler.kt 'redactionFilter.redact(event.detail)'
text src/main/kotlin/atropos/bridge/BridgeEditorHandler.kt 'HttpResponse.json(redactionFilter.redact(context()))'
text src/main/kotlin/atropos/bridge/BridgeSelfHostHandler.kt 'redactionFilter.compact(result.message)'
text src/main/kotlin/atropos/bridge/BridgeSelfHostHandler.kt 'redactionFilter.compact(advanced.message)'
text src/main/kotlin/atropos/bridge/BridgeSelfHostHandler.kt 'redactionFilter.redact("bridge_start'
text src/main/kotlin/atropos/bridge/BridgeFilesHandler.kt 'redactionFilter.compact('
text src/main/kotlin/atropos/bridge/BridgeFilesHandler.kt 'execution.refusalReason'
text src/main/kotlin/atropos/bridge/BridgeInboundToolHandler.kt 'redactionFilter.redact(result.decision.reason)'
text src/main/kotlin/atropos/bridge/BridgeMcpHandler.kt 'redactionFilter.redact(status.reason)'
text src/main/kotlin/atropos/bridge/BridgeApprovalHandler.kt 'redactionFilter.compact(outcome.reason)'
text src/main/kotlin/atropos/core/integration/MarkItDownIngestService.kt 'MAX_SOURCE_BYTES'
text src/main/kotlin/atropos/core/integration/MarkItDownIngestService.kt 'Files.isSymbolicLink(source)'
text src/main/kotlin/atropos/core/integration/MarkItDownIngestService.kt 'StandardCopyOption.ATOMIC_MOVE'

# Local Git worktree creation stays on the existing bounded typed Git owner.
file src/main/kotlin/atropos/core/worktree/BoundedGitWorktreeCommandRunner.kt
file src/main/kotlin/atropos/core/worktree/IsolatedWorktreeService.kt
file src/main/kotlin/atropos/cli/commands/AgentWorktreeCommandHandler.kt
text src/main/kotlin/atropos/core/worktree/BoundedGitWorktreeCommandRunner.kt 'GitWorktreeOperation.WORKTREE_ADD'
text src/main/kotlin/atropos/core/worktree/IsolatedWorktreeService.kt 'fun createWorktree'
text src/main/kotlin/atropos/core/worktree/IsolatedWorktreeService.kt 'GitWorktreeOperation.WORKTREE_ADD'
text src/main/kotlin/atropos/cli/commands/AgentWorktreeCommandHandler.kt 'worktreeService.createWorktree'

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
