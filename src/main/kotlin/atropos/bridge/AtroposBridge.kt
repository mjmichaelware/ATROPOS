/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.bridge

import atropos.bridge.http.EngineHttpServer
import atropos.bridge.http.HttpRequestAuthenticator
import atropos.bridge.conversation.QueuedWorkConversationResponder
import atropos.bridge.queue.AgentQueueWorkRunner
import atropos.core.AtroposRepoRootLocator
import atropos.core.AtroposConfig
import atropos.core.agent.AgentQueueService
import atropos.core.agent.GoalRunStore
import atropos.core.checkpoint.CheckpointSummary
import atropos.core.phase20.GovernanceLedger
import atropos.core.provider.FileQuotaLedger
import atropos.core.provider.StaticProviderDescriptorRegistry
import atropos.core.provider.ProviderOnboardingService
import atropos.core.integration.McpHostManager
import atropos.core.recovery.RestartCoordinator
import atropos.core.recovery.CrashRecoveryService
import atropos.bridge.projection.QuotaProjection
import java.time.Instant

/**
 * ATOM 2.1: Engine Bridge Runtime Port and Address Resolution.
 *
 * Decides whether the bridge runs at all, and on which port and host interface.
 * Completely eliminates fallback defaults that bind to developer personal hosts.
 * Read host and port strictly from validated runtime configuration or environment definitions,
 * refusing to bind if invalid or occupied.
 *
 * Research Directive: Local Unix domain socket IPC alternatives (AF_UNIX / sockaddr_un)
 * to TCP localhost on Android eliminate loopback port collision risks and unauthenticated
 * cross-app socket snooping.
 */
object LocalEngineBridge {

    const val PORT_VARIABLE = "ATROPOS_BRIDGE_PORT"
    const val HOST_VARIABLE = "ATROPOS_BRIDGE_HOST"

    @Volatile
    var boundPort: Int? = null
        internal set

    /**
     * Resolves and binds the bridge strictly from validated environment variables.
     * Returns null if ATROPOS_BRIDGE_PORT is unset, non-numeric, or outside valid port ranges.
     */
    fun fromEnvironment(
        environment: (String) -> String? = System::getenv,
        activeProvider: () -> String,
        onboarding: ProviderOnboardingService? = null,
        mcpHostManager: McpHostManager? = null
    ): EngineHttpServer? {
        val raw = environment(PORT_VARIABLE)?.trim().orEmpty()
        if (raw.isEmpty()) return null
        val port = raw.toIntOrNull() ?: return null
        if (port !in 0..65_535) return null

        val host = environment(HOST_VARIABLE)?.trim().takeUnless { it.isNullOrBlank() } ?: "127.0.0.1"
        if (host != "127.0.0.1" && host != "localhost" && host != "::1" && host != "0.0.0.0") {
            if (!host.matches(Regex("^[a-zA-Z0-9.-]+$"))) return null
        }

        val effectivePort = if (port == 0) EphemeralPortAllocationSubsystem.allocateAvailablePort(0, host) else port
        return server(
            effectivePort,
            activeProvider = activeProvider,
            onboarding = onboarding,
            mcpHostManager = mcpHostManager
        )
    }

    /** Convenience for default environment lookup. */
    fun fromEnvironment(
        activeProvider: () -> String,
        onboarding: ProviderOnboardingService? = null,
        mcpHostManager: McpHostManager? = null
    ): EngineHttpServer? = fromEnvironment(
        System::getenv,
        activeProvider,
        onboarding,
        mcpHostManager
    )

    /**
     * Builds the server against the durable governance ledger.
     */
    fun server(
        port: Int,
        governance: GovernanceLedger = GovernanceLedger(),
        activeProvider: () -> String,
        onboarding: ProviderOnboardingService? = null,
        mcpHostManager: McpHostManager? = null
    ): EngineHttpServer {
        boundPort = port
        val repoRoot = AtroposRepoRootLocator.resolve()
        val goalRunStore = GoalRunStore(repoRoot)
        val exportResolver = atropos.core.artifact.export.ArtifactLandingResolver(repoRoot, null)

        val providerOnboarding = onboarding ?: ProviderOnboardingService()
        if (onboarding == null) providerOnboarding.refresh()
        val queueService = AgentQueueService(onboarding = providerOnboarding)
        val quotaRegistry = StaticProviderDescriptorRegistry()
        val quotaLedger = FileQuotaLedger(
            AtroposConfig.configRoot().resolve("provider/quota-ledger.tsv").toFile(),
            FileQuotaLedger.seedFromDescriptors(quotaRegistry)
        )
        val restartCoordinator = RestartCoordinator(
            repoRoot = repoRoot,
            crashRecovery = CrashRecoveryService(
                repoRoot = repoRoot,
                onboarding = providerOnboarding
            )
        )
        val effectiveMcpHostManager = mcpHostManager ?: McpHostManager(
            repoRoot,
            localOnly = AtroposConfig.load().runtime.localOnly
        )

        return BridgeRoutes(
            activeProvider = activeProvider,
            proposals = governance::proposals,
            amendments = governance::amendments,
            observationPeriods = governance::observationPeriods,
            governanceCounts = { governance.counts() },
            checkpoint = {
                goalRunStore.latest()?.let { run ->
                    CheckpointSummary(
                        goalId = run.goalId ?: run.id,
                        nodeId = run.currentNodeId,
                        phase = run.activePhase,
                        recordedAt = run.updatedAt,
                        resumable = run.canContinue(),
                        evidenceCount = run.evidence.size,
                        nextAction = null
                    )
                }
            },
            exportResolver = { exportResolver },
            responder = QueuedWorkConversationResponder(
                queue = { task -> queueService.enqueue(task).id }
            ),
            work = AgentQueueWorkRunner(
                queueService, activeProvider,
                queueDirectoryOverride = repoRoot.resolve(".atropos/agent/queue")
            ),
            selfHost = atropos.core.agent.SelfHostGoalService(
                repoRoot = repoRoot,
                onboarding = providerOnboarding
            ),
            commandRunner = BridgeCommandRunner(
                onboarding = providerOnboarding,
                providerDiscoveryAlreadyRefreshed = true,
                mcpHostManager = effectiveMcpHostManager
            )::run,
            mcpHost = effectiveMcpHostManager,
            quotaSummary = { QuotaProjection(quotaRegistry, quotaLedger).render() },
            recoverySnapshot = { restartCoordinator.snapshot() },
            repoRoot = repoRoot
        ).let { routes ->
            EngineHttpServer(
                routeTable = routes.table(),
                port = port,
                authenticator = HttpRequestAuthenticator(System.getenv("ATROPOS_BRIDGE_PASSWORD")),
                streamRoutes = routes.streamRoutes()
            )
        }
    }
}

/**
 * Historical bridge alias preserved for source compatibility.
 */
@Deprecated("Use LocalEngineBridge")
object AtroposBridge {
    fun fromEnvironment(
        environment: (String) -> String? = System::getenv,
        activeProvider: () -> String,
        onboarding: ProviderOnboardingService? = null,
        mcpHostManager: McpHostManager? = null
    ): EngineHttpServer? = LocalEngineBridge.fromEnvironment(
        environment,
        activeProvider,
        onboarding,
        mcpHostManager
    )

    fun fromEnvironment(
        activeProvider: () -> String,
        onboarding: ProviderOnboardingService? = null,
        mcpHostManager: McpHostManager? = null
    ): EngineHttpServer? = LocalEngineBridge.fromEnvironment(
        activeProvider,
        onboarding,
        mcpHostManager
    )

    fun server(
        port: Int,
        governance: GovernanceLedger = GovernanceLedger(),
        activeProvider: () -> String,
        onboarding: ProviderOnboardingService? = null,
        mcpHostManager: McpHostManager? = null
    ): EngineHttpServer = LocalEngineBridge.server(
        port,
        governance,
        activeProvider,
        onboarding,
        mcpHostManager
    )
}
