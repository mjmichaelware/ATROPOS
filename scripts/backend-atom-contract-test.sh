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
text src/main/kotlin/atropos/core/autonomous/ProviderWorkerDirector.kt 'policyGate.isEligible(task.providerId, capability)'
text src/main/kotlin/atropos/core/provider/ProviderOnboarding.kt 'fun isEligible(providerId: String, capability: ApiCapability)'
text src/test/kotlin/atropos/core/autonomous/ProviderWorkerDirectorTest.kt 'local_only_policy_refuses_remote_free_worker_before_hierarchy_dispatch'
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
for provider_id in \
  openai anthropic groq xai gemini openrouter together fireworks \
  deepseek_direct mistral cohere ollama aws_bedrock azure_openai; do
  text src/main/kotlin/atropos/core/provider/StaticProviderDescriptorRegistry.kt "d(\"$provider_id\""
done
text src/main/kotlin/atropos/core/provider/adapter/BuildKernelAdapter.kt 'AnthropicKernelAdapter(descriptor'
text src/main/kotlin/atropos/core/provider/adapter/BuildKernelAdapter.kt 'BedrockKernelAdapter(descriptor, env)'
for provider_id in openai groq xai openrouter together fireworks deepseek_direct mistral cohere; do
  text src/main/kotlin/atropos/core/provider/adapter/OpenAiCompatibleProviderCatalog.kt "providerId = \"$provider_id\""
done
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
text src/main/kotlin/atropos/Main.kt 'val providerOnboarding = atropos.core.provider.ProviderOnboardingService()'
text src/main/kotlin/atropos/Main.kt 'providerOnboarding.refresh()'
text src/main/kotlin/atropos/Main.kt 'providerOnboarding.renderLaunchSummary(refresh = false)'
text src/main/kotlin/atropos/Main.kt 'providerDiscoveryAlreadyRefreshed = true'
text src/main/kotlin/atropos/cli/CommandRouter.kt 'if (providerDiscoveryAlreadyRefreshed)'
text src/main/kotlin/atropos/cli/CommandRouter.kt 'providerOnboarding.list()'
text src/main/kotlin/atropos/cli/CommandRouter.kt 'BackendDoctor(config, providerOnboarding)'
text src/main/kotlin/atropos/Main.kt 'BackendDoctor(config, providerOnboarding)'
text src/main/kotlin/atropos/cli/CommandRouter.kt 'ProviderCommandHandler(config, uiEngine, onboarding = providerOnboarding)'
text src/main/kotlin/atropos/cli/ProviderCommandHandler.kt 'private val onboarding: ProviderOnboardingService'
text src/main/kotlin/atropos/cli/ProviderChatDispatcher.kt 'healthyProviderIds = { onboarding.healthyProviderIds() }'
text src/main/kotlin/atropos/cli/ProviderChatDispatcher.kt 'preferredProviderIds = { onboarding.preferredProviderIds() }'
text src/main/kotlin/atropos/cli/CommandRouter.kt 'onboarding = providerOnboarding'
text src/main/kotlin/atropos/cli/CommandRouter.kt 'preferredProviderIds = { providerOnboarding.preferredProviderIds() }'
text src/main/kotlin/atropos/cli/StatusCommandHandler.kt 'onboarding = providerOnboarding'
text src/main/kotlin/atropos/cli/StatusCommandHandler.kt 'StatusAdapterRenderer(onboarding = providerOnboarding)'
text src/main/kotlin/atropos/cli/ui/StatusAdapterRenderer.kt 'AdapterRouteFacade(onboarding = onboarding)'
text src/main/kotlin/atropos/core/autonomous/AutonomousOrchestrator.kt 'ProviderWorkerDirector(onboarding = onboarding)'
text src/main/kotlin/atropos/core/agent/AgentQueueService.kt 'AgentQueueProcessor(config, collector, onboarding = onboarding)'
text src/main/kotlin/atropos/core/agent/AgentQueueProcessor.kt 'AgentQueueExecutor(config, collector, onboarding = onboarding'
text src/main/kotlin/atropos/core/agent/AgentQueueExecutor.kt 'AgentRunService(config, collector, onboarding)'
text src/main/kotlin/atropos/bridge/AtroposBridge.kt 'AgentQueueService(onboarding = providerOnboarding)'
text src/main/kotlin/atropos/cli/StatusCommandHandler.kt 'healthyProviderIds = providerOnboarding::healthyProviderIds'
text src/main/kotlin/atropos/cli/ui/StatusQuotaRenderer.kt 'preferredProviderIds = { onboarding.preferredProviderIds() }'
text src/main/kotlin/atropos/core/agent/AgentService.kt 'healthyProviderIds = { onboarding.healthyProviderIds() }'
text src/main/kotlin/atropos/core/agent/AgentService.kt 'preferredProviderIds = { onboarding.preferredProviderIds() }'
text src/main/kotlin/atropos/core/agent/AgentRepairService.kt 'healthyProviderIds = { onboarding.healthyProviderIds() }'
text src/main/kotlin/atropos/core/agent/AgentRepairService.kt 'preferredProviderIds = { onboarding.preferredProviderIds() }'
text src/main/kotlin/atropos/core/agent/AgentRunService.kt 'AgentService(config, collector, onboarding)'
text src/main/kotlin/atropos/cli/commands/AgentCommand.kt 'providerOnboarding = providerOnboarding'
text src/main/kotlin/atropos/core/provider/SourceBindingFetcher.kt 'localOnly'
text src/main/kotlin/atropos/core/provider/SourceBindingFetcher.kt 'disabled by localOnly'
text src/main/kotlin/atropos/core/provider/ProviderOnboarding.kt 'genericProviderIds'
text src/main/kotlin/atropos/core/provider/ProviderEnvironmentAliases.kt 'ATROPOS_PROVIDER_$canonical'
text src/main/kotlin/atropos/core/provider/ProviderEnvironmentAliases.kt 'ATROPOS_PROVIDER_$it'
text src/main/kotlin/atropos/core/provider/ProviderOnboarding.kt 'filterNot { key ->'
text src/main/kotlin/atropos/core/provider/ProviderActivationService.kt 'ProviderEnvironmentAliases.names(required)'
text src/main/kotlin/atropos/core/provider/ProviderConfigurationResolver.kt 'ProviderEnvironmentAliases.names(name)'
text src/main/kotlin/atropos/core/provider/ProviderConfigurationResolver.kt 'DefaultSecretSource.create(env = environment)'
text src/main/kotlin/atropos/core/provider/ProviderConfigurationResolver.kt 'secretSource.lookup(candidate).configured'
text src/test/kotlin/atropos/core/provider/ProviderConfigurationResolverTest.kt 'truth_resolver_accepts_provider_connected_through_local_secret_source'
text src/test/kotlin/atropos/core/provider/ProviderConfigurationResolverTest.kt 'actual_provider_connect_vault_is_visible_to_canonical_truth_resolver'
file src/main/kotlin/atropos/core/agent/ImportedInstructionPackStore.kt
text src/main/kotlin/atropos/cli/commands/AgentCommand.kt 'importedInstructionPacks.import(arguments[1])'
text src/main/kotlin/atropos/core/agent/ImportedInstructionPackStore.kt 'OVERRIDE_POLICY=Source Docs and ATROPOS policy remain authoritative'
text src/main/kotlin/atropos/core/agent/ImportedInstructionPackStore.kt 'redactionFilter.redact(Files.readString(source'
text src/main/kotlin/atropos/core/agent/ImportedInstructionPackStore.kt 'require(source.startsWith(repoRoot))'
text src/main/kotlin/atropos/core/agent/ImportedInstructionPackStore.kt 'pack.hasValidContentHash()'
file src/main/kotlin/atropos/core/factory/FactoryResearchService.kt
file src/main/kotlin/atropos/cli/BackendDoctor.kt
file docs/OPEN_CORE_BOUNDARY.md
file LEGAL.md
text src/main/kotlin/atropos/core/Config.kt 'zero_retention_research'
text src/main/kotlin/atropos/core/factory/FactoryResearchService.kt 'zeroRetentionResearch'
text src/main/kotlin/atropos/core/factory/FactoryResearchService.kt 'lakehouse_route=SKIPPED_ZERO_RETENTION:remote_research_disabled'
text src/main/kotlin/atropos/core/factory/FactoryResearchService.kt 'bounded_fetch=SKIPPED_ZERO_RETENTION'
text src/main/kotlin/atropos/cli/BackendDoctor.kt 'zero_retention_research=${config.runtime.zeroRetentionResearch}'
text docs/OPEN_CORE_BOUNDARY.md 'local-only mode'
text README.md 'docs/OPEN_CORE_BOUNDARY.md'
text README.md 'LEGAL.md'
text README.md 'docs/PROVIDER_ENVIRONMENT.md'
file scripts/provider-env-readme-generator.py
text scripts/provider-env-contract-test.sh 'provider-env-readme-generator.py" --check'
text README.md '<!-- BEGIN GENERATED PROVIDER ENVIRONMENT TABLE -->'
text CONTRIBUTING.md 'Do not add one Kotlin adapter per brand'
text docs/OPEN_CORE_BOUNDARY.md 'AGPL section 13'
text LEGAL.md 'AGPL section 13'
text LEGAL.md 'open-core boundary'
text scripts/atropos-verify-worktree.sh "atropos.core.provider.ProviderConfigurationResolverTest"
text .github/workflows/compile-gate.yml "atropos.core.provider.ProviderConfigurationResolverTest"
text scripts/atropos-verify-worktree.sh "atropos.ast.AstSymbolGraphTest"
text scripts/atropos-verify-worktree.sh "atropos.dloi.SourceAuthorityLawTest"
text .github/workflows/compile-gate.yml "atropos.data.lakehouse.AtomLakehouseContextTest"
text .github/workflows/compile-gate.yml "atropos.core.acceptance.CanonicalAcceptanceTests"
text scripts/hosted-test-selector-contract.sh "Tests?"
text scripts/hosted-test-selector-contract.sh 'backend test source is outside hosted selector'
text src/main/kotlin/atropos/core/provider/ProviderOnboarding.kt 'fun preferredProviderIds()'
text src/main/kotlin/atropos/core/provider/ProviderOnboarding.kt 'fun disable(providerId: String)'
text src/main/kotlin/atropos/core/provider/adapter/AdapterRouteFacade.kt 'healthyProviderIds = { onboarding.healthyProviderIds() }'
text src/main/kotlin/atropos/core/provider/adapter/AdapterRouteFacade.kt 'preferredProviderIds = { onboarding.preferredProviderIds() }'
text src/main/kotlin/atropos/cli/RouteCommandHandler.kt 'onboarding = onboarding'
text src/main/kotlin/atropos/cli/HistoryCommandHandler.kt 'historyStore.searchAll(HistoryQuery(limit = limit))'
text src/main/kotlin/atropos/cli/HistoryCommandHandler.kt 'private fun toHistoryEntry(event: ExecutionEvent)'
text src/main/kotlin/atropos/core/observability/ExecutionHistoryStore.kt 'EventJournalService(repoRoot).record('
text src/main/kotlin/atropos/core/observability/ExecutionHistoryStore.kt 'reindex(runId)'
text src/main/kotlin/atropos/core/provider/RoutePolicy.kt 'preferredProviderIds?.invoke()?.indexOf(providerId)'

# Help and installer callers. These atoms are deliberately guarded here rather
# than by a second catalog: the CLI help owner is CommandRouter -> HelpGenerator
# -> CommandRegistry, and the installer owner is the single install.sh path.
file src/main/kotlin/atropos/cli/help/HelpGenerator.kt
file src/main/kotlin/atropos/cli/input/CommandRegistry.kt
text src/main/kotlin/atropos/cli/CommandRouter.kt 'renderHelpPage(routedTokens.drop(1).joinToString(" "))'
text src/main/kotlin/atropos/cli/CommandRouter.kt 'HelpGenerator().render(level)'
text src/main/kotlin/atropos/cli/help/HelpGenerator.kt 'RegistryHelpSource'
text src/main/kotlin/atropos/cli/input/CommandCatalog.kt 'CommandEntry("/help"'
text install.sh 'OS_NAME=$(uname -s'
text install.sh 'aarch64|arm64) CPU_ARCH=aarch64'
text install.sh 'x86_64|amd64) CPU_ARCH=x86_64'
text install.sh 'Darwin) PLATFORM="darwin-$CPU_ARCH"'
text install.sh 'export ATROPOS_PLATFORM='
text scripts/install-contract-test.sh 'ATROPOS_INSTALL_CONTRACT_OK'
text src/main/kotlin/atropos/core/provider/SourceBindingFetcher.kt 'connectTimeout(java.time.Duration.ofSeconds(5))'
text src/main/kotlin/atropos/core/provider/SourceBindingFetcher.kt 'SecretSinkMatrix.isEgressPermitted'
text src/main/kotlin/atropos/core/provider/SourceBindingFetcher.kt 'HttpClient.Redirect.NEVER'
text src/main/kotlin/atropos/core/github/GitHubDeviceAuthClient.kt 'GitHub OAuth disabled by local-only mode'
text src/main/kotlin/atropos/core/github/GitHubDeviceAuthClient.kt 'requireJsonObject(response.body)'
text src/main/kotlin/atropos/core/github/GitHubDeviceAuthClient.kt 'vault.writeSecret("GITHUB_TOKEN"'
text src/main/kotlin/atropos/cli/AuthCommandHandler.kt '"github" -> renderGitHub()'
text src/main/kotlin/atropos/cli/AuthCommandHandler.kt 'redactionFilter.compact(it.message'
text src/main/kotlin/atropos/cli/input/CommandCatalog.kt '"/auth github"'
text .github/actions/atropos-verify/action.yml 'verify-script must stay inside working-directory'
text .github/actions/atropos-verify/action.yml 'test -f "$VERIFY_SCRIPT"'
text .github/actions/atropos-verify/action.yml 'grep -Eo '\''[0-9a-fA-F]{64}'\'' "$log_file"'
text .github/actions/atropos-verify/action.yml 'exit "$verify_exit"'
text .github/workflows/atropos-verify-example.yml 'uses: actions/github-script@v7'
text .github/workflows/atropos-verify-example.yml 'checks.create'
text .github/workflows/atropos-verify-example.yml 'conclusion = outcome === '\''success'\'' ? '\''success'\'''

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
test "$(rg -F 'remoteExchange(server, "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}", maxResponseBytes)' "$ROOT/src/main/kotlin/atropos/core/integration/McpHostManager.kt" | wc -l | tr -d ' ')" = 1 || { echo "BACKEND_ATOM_CONTRACT_FAIL duplicate remote initialized notification" >&2; exit 1; }
text src/main/kotlin/atropos/core/integration/McpHostManager.kt 'writer.write("{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}'
text src/test/kotlin/atropos/core/integration/McpHostManagerTest.kt 'assertEquals(3, requests.size)'
text src/test/kotlin/atropos/core/integration/McpHostManagerTest.kt 'everything_ref_test_fixture_is_disabled_by_default_and_visible_to_status'
text src/test/kotlin/atropos/core/integration/McpHostManagerTest.kt 'requests[1].contains("notifications/initialized")'
text src/main/kotlin/atropos/core/integration/McpHostManager.kt 'listOf(command) + server.args'
text src/main/kotlin/atropos/core/integration/McpHostManager.kt 'environment = runtimeEnvironment(server)'
text src/main/kotlin/atropos/core/integration/McpHostManager.kt 'environment = environment'
text src/main/kotlin/atropos/core/integration/McpHostManager.kt 'runtimeEnvironment(server)'
text src/main/kotlin/atropos/core/integration/McpHostManager.kt 'secretSource.lookup(reference).value'
text src/main/kotlin/atropos/core/integration/McpHostManager.kt 'must use an environment or vault reference'
text src/main/kotlin/atropos/core/integration/McpConfigParser.kt 'rawMember(objectText, "env")?.let(::stringMap)'
text src/main/kotlin/atropos/core/integration/McpConfigParser.kt 'rawMember(objectText, "headers")?.let(::stringMap)'
text src/main/kotlin/atropos/core/integration/McpHostManager.kt 'runtimeHeaders(server)'
text src/main/kotlin/atropos/core/integration/McpHostManager.kt 'before either the real HTTP owner'
text src/main/kotlin/atropos/core/integration/McpHostManager.kt 'MCP header cannot override transport framing'
text src/main/kotlin/atropos/core/integration/McpHostManager.kt 'header(name, value)'
text src/main/kotlin/atropos/core/integration/McpConfigParser.kt 'MAX_ENV_ENTRIES = 32'
text src/test/kotlin/atropos/core/integration/McpHostManagerTest.kt 'stdio_server_receives_declared_environment_through_bounded_runner'
text src/test/kotlin/atropos/core/integration/McpHostManagerTest.kt 'secret_named_environment_must_reference_existing_secret_source'
text src/test/kotlin/atropos/core/integration/McpHostManagerTest.kt 'secret_named_remote_header_must_reference_existing_secret_source'
text src/main/kotlin/atropos/bridge/projection/QuotaProjection.kt '"latencyMsAvg"'
text src/main/kotlin/atropos/bridge/projection/QuotaProjection.kt '"successScore"'
text src/main/kotlin/atropos/bridge/projection/QuotaProjection.kt '"costPerVerifiedPredicateTokens"'
text src/main/kotlin/atropos/core/provider/QuotaLedger.kt 'recordVerifiedPredicate'
text src/main/kotlin/atropos/core/provider/ProviderActivationService.kt 'provider_activation_live_probe'
text src/main/kotlin/atropos/core/provider/ProviderEnvironmentAliases.kt 'fun forProvider'
text src/main/kotlin/atropos/core/provider/ProviderOnboarding.kt 'ProviderEnvironmentAliases.forProvider'
text src/main/kotlin/atropos/core/provider/adapter/AdapterRouteFacade.kt 'ledger.recordSuccess'
text src/main/kotlin/atropos/core/provider/adapter/AdapterRouteFacade.kt 'ledger.recordFailure'
text src/main/kotlin/atropos/core/provider/adapter/AdapterRouteFacade.kt 'ProviderQuotaPaths.defaultLedger()'
text src/main/kotlin/atropos/cli/ui/StatusQuotaRenderer.kt 'ProviderQuotaPaths.defaultLedger()'
text src/main/kotlin/atropos/cli/ui/StatusQuotaRenderer.kt 'healthyProviderIds = healthyProviderIds'
text src/main/kotlin/atropos/core/testing/AtroposTestMatrix.kt 'healthyProviderIds = { fixtureHealthy }'
text src/main/kotlin/atropos/core/provider/ProviderOnboarding.kt 'healthy and enabled before it can be preferred'
text src/main/kotlin/atropos/core/provider/QuotaLedger.kt 'mergeSeed(load(file), seed)'
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
text src/main/kotlin/atropos/core/integration/McpHostManager.kt 'private val toolExecutor: TypedToolExecutor = TypedToolExecutor()'
text src/main/kotlin/atropos/core/integration/McpHostManager.kt 'val execution = toolExecutor.execute(judged.decision)'
text src/main/kotlin/atropos/bridge/BridgeMcpHandler.kt 'manager.callTool('
text src/main/kotlin/atropos/bridge/AtroposBridge.kt 'mcpHost = atropos.core.integration.McpHostManager('
text src/main/kotlin/atropos/bridge/AtroposBridge.kt 'quotaSummary = { QuotaProjection(quotaRegistry, quotaLedger).render() }'
text src/main/kotlin/atropos/bridge/AtroposBridge.kt 'work = AgentQueueWorkRunner(queueService, activeProvider)'
text src/main/kotlin/atropos/bridge/AtroposBridge.kt 'responder = QueuedWorkConversationResponder('
text src/main/kotlin/atropos/bridge/AtroposBridge.kt 'recoverySnapshot = { restartCoordinator.snapshot() }'
text src/main/kotlin/atropos/cli/ShellCommandHandler.kt 'shellRunner.gitStatus()'
text src/main/kotlin/atropos/cli/ShellCommandHandler.kt 'shellRunner.gitDiff()'
text src/main/kotlin/atropos/cli/shell/ShellCommandRunner.kt 'BoundedProcessRunner().start(command, directory.toPath())'
text src/main/kotlin/atropos/cli/shell/ShellCommandRunner.kt 'fun gitConflicts()'
file src/main/kotlin/atropos/cli/GitMutationCommand.kt
text src/main/kotlin/atropos/cli/GitMutationCommand.kt '"add"'
text src/main/kotlin/atropos/cli/GitMutationCommand.kt '"commit"'
text src/main/kotlin/atropos/cli/GitMutationCommand.kt '"rebase-continue"'
text src/main/kotlin/atropos/cli/GitMutationCommand.kt '--confirm'
text src/main/kotlin/atropos/cli/ShellCommandHandler.kt 'GitMutationCommandParser.parse(tokens)'
text src/main/kotlin/atropos/cli/shell/ShellCommandRunner.kt 'fun runGitMutation(command: List<String>, targetPaths: List<String>)'
text src/main/kotlin/atropos/cli/shell/ShellCommandRunner.kt 'PolicyActionClass.FILE_MUTATION'
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
for github_method in \
  listIssues getIssue createIssue commentIssue listPullRequests getPullRequestFiles \
  createPullRequest commentPullRequest requestPullReview listCheckRuns \
  createCheckRun updateCheckRun getBranchProtection; do
  text src/main/kotlin/atropos/core/github/GitHubApiClient.kt "fun $github_method"
  text src/main/kotlin/atropos/core/github/GitHubBinding.kt "fun $github_method"
done
text src/main/kotlin/atropos/core/github/GitHubApiClient.kt 'GitHubWriteAuthorization'
text src/main/kotlin/atropos/core/github/GitHubApiClient.kt 'declaredTerritory'
text src/main/kotlin/atropos/core/github/GitHubApiClient.kt 'SecretSinkKind.EGRESS_URL'
text src/test/kotlin/atropos/core/github/GitHubApiClientTest.kt 'typed_issue_pr_review_and_check_endpoints_share_the_same_transport'
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
text src/main/kotlin/atropos/bridge/BridgeRoutes.kt 'BridgeFilesHandler(repoRoot)'
text src/main/kotlin/atropos/bridge/BridgeRoutes.kt 'BridgeEvidenceHandler(work, repoRoot)'
text src/main/kotlin/atropos/bridge/AtroposBridge.kt 'repoRoot = repoRoot'
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
text src/main/kotlin/atropos/core/factory/FactoryRunOrchestrator.kt 'repairVerificationFailure'
text src/main/kotlin/atropos/core/factory/FactoryRunOrchestrator.kt 'FactoryRepairExecutor(obligationLoop).repairAndResume'
text src/main/kotlin/atropos/core/factory/FactoryRunOrchestrator.kt 'state = if (repairWasExecuted) "REENTERED_OBLIGATION_LOOP" else "NOT_NEEDED"'
text src/main/kotlin/atropos/core/factory/FactoryRepairExecutor.kt 'obligationLoop.recordRepairEvidence'
text src/main/kotlin/atropos/core/factory/FactoryAcceptanceFreeze.kt 'repair acceptance predicates did not all pass'
text src/main/kotlin/atropos/core/factory/AppFactoryRouter.kt 'onboarding = onboarding'
text src/main/kotlin/atropos/core/factory/SpecGraphCanonicalAtomProvider.kt 'preferredProviderIds = { onboarding.preferredProviderIds() }'
text scripts/mcp-example-contract-test.sh 'optional-catalog.json'
text docs/mcp-examples/optional-catalog.json '"enabled":false'

# Both hosted entrypoints must run this contract.
text .github/workflows/compile-gate.yml 'backend-atom-contract-test.sh'
text scripts/atropos-verify-worktree.sh 'backend-atom-contract-test.sh'
text .github/workflows/factory-test.yml 'actions/checkout@v4'
text .github/workflows/factory-test.yml 'actions/setup-java@v4'
text .github/workflows/factory-test.yml 'gradle/actions/setup-gradle@v4'
text .github/workflows/factory-test.yml 'github-actions-clean-runner-test.sh'
text .github/workflows/release.yml 'backend-atom-contract-test.sh'
text .github/workflows/release.yml 'github-actions-clean-runner-test.sh'
text .github/workflows/release.yml 'provider-connect-contract-test.sh'
text .github/workflows/release.yml 'mcp-example-contract-test.sh'
text .github/workflows/release.yml 'Prove version reports the published hash'
text src/main/kotlin/atropos/core/BuildStamp.kt 'artifactSha256'
text src/main/kotlin/atropos/Main.kt 'BuildStamp.line()'
text src/main/kotlin/atropos/Main.kt 'args.firstOrNull() == "doctor"'
text src/main/kotlin/atropos/Main.kt 'args.firstOrNull() == "-h"'
text src/main/kotlin/atropos/Main.kt 'HelpGenerator().render'
text src/main/kotlin/atropos/Main.kt 'args.firstOrNull() == "doctor" && args.drop(1).any'

# The status ledger is append-only evidence, not a second implementation. Keep
# the authority's in-scope backend atoms visible so a source-only change cannot
# silently erase the caller/evidence trail.
for atom_id in \
  B-INST-001 B-INST-002 B-INST-003 B-INST-004 B-INST-005 B-INST-006 \
  B-PROV-001 B-PROV-002 B-PROV-003 B-PROV-004 B-PROV-005 B-PROV-006 \
  B-HELP-001 B-HELP-002 \
  B-MCP-SENTRY B-MCP-GHA B-MCP-GITHUB B-MCP-GITLOCAL B-MCP-FS \
  B-MCP-GITLAB B-MCP-BITBUCKET B-MCP-LINEAR B-MCP-JIRA B-MCP-CONFLUENCE \
  B-MCP-PLAYWRIGHT B-MCP-PUPPETEER B-MCP-DOCKER B-MCP-POSTGRES B-MCP-SQLITE \
  B-MCP-REDIS B-MCP-SNYK B-MCP-SONAR B-MCP-DATADOG B-MCP-NEWRELIC \
  B-MCP-SUPABASE B-MCP-FIREBASE B-MCP-SLACK B-MCP-DISCORD B-MCP-TEAMS \
  B-MCP-ASANA B-MCP-CLICKUP B-MCP-NOTION B-MCP-PAGERDUTY B-MCP-OPSGENIE \
  B-MCP-TERRAFORM B-MCP-PULUMI B-MCP-K8S B-MCP-AWS-READ B-MCP-GCP-READ \
  B-MCP-AZURE-READ B-MCP-SEQUENTIAL-THINKING \
  B-MCP-BRIDGE-SCHEMA B-MCP-MEMORY B-MCP-FETCH B-MCP-TIME B-MCP-EVERYTHING-REF \
  B-MCP-OAUTH-UX B-MCP-KEYCHAIN B-OC-001 B-OC-002 B-OC-003 B-OC-004; do
  rg -Fq -- "$atom_id" "$ROOT/STATUS-BACKEND.md" || {
    echo "BACKEND_ATOM_CONTRACT_FAIL missing status ledger atom $atom_id" >&2
    exit 1
  }
done

printf '%s\n' 'ATROPOS_BACKEND_ATOM_CONTRACT_OK'
