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
import atropos.core.recovery.RestartCoordinator
import atropos.bridge.projection.QuotaProjection
import java.time.Instant

/**
 * Decides whether the bridge runs at all, and on which port.
 *
 * Opt-in by construction. A listener that opens on every start is a surface the
 * operator never chose, and §6 treats anything that widens the reachable
 * surface as a decision that belongs to them. `ATROPOS_BRIDGE_PORT` is both the
 * switch and the value: absent means no listener, so there is no way to end up
 * with a port open because a default changed underneath.
 *
 * `0` is honoured as "let the OS choose", which is what makes the bridge usable
 * from a test without racing a fixed port.
 */
object LocalEngineBridge {

    const val PORT_VARIABLE = "ATROPOS_BRIDGE_PORT"

    fun fromEnvironment(
        environment: (String) -> String? = System::getenv,
        activeProvider: () -> String
    ): EngineHttpServer? {
        val raw = environment(PORT_VARIABLE)?.trim().orEmpty()
        if (raw.isEmpty()) return null
        val port = raw.toIntOrNull() ?: return null
        if (port !in 0..65_535) return null
        return server(port, activeProvider = activeProvider)
    }

    /** Convenience for the common call shape: default environment lookup. */
    fun fromEnvironment(activeProvider: () -> String): EngineHttpServer? =
        fromEnvironment(System::getenv, activeProvider)

    /**
     * Builds the server against the durable governance ledger.
     *
     * Wired here rather than defaulted inside [BridgeRoutes] so the routes stay
     * constructible without touching a filesystem — a test that wanted to check
     * one projection should not have to own a repository root. This is the one
     * place that decides the running bridge reads real state.
     */
    fun server(
        port: Int,
        governance: GovernanceLedger = GovernanceLedger(),
        activeProvider: () -> String
    ): EngineHttpServer {
        val repoRoot = AtroposRepoRootLocator.resolve()
        val goalRunStore = GoalRunStore(repoRoot)
        val exportResolver = atropos.core.artifact.export.ArtifactLandingResolver(repoRoot, null)
        // One queue instance serves both enqueueing and running, so a client
        // runs the very entries it created.
        val queueService = AgentQueueService()
        val quotaRegistry = StaticProviderDescriptorRegistry()
        val quotaLedger = FileQuotaLedger(
            AtroposConfig.configRoot().resolve("provider/quota-ledger.tsv").toFile(),
            FileQuotaLedger.seedFromDescriptors(quotaRegistry)
        )
        val restartCoordinator = RestartCoordinator(repoRoot)

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
            exportTerritory = { listOf(repoRoot) },
            // This is where a phone message becomes real work. Constructed here
            // rather than defaulted inside BridgeRoutes so the routes stay
            // buildable without a repository: a test checking one projection
            // must not have to own a queue on disk.
            //
            // Enqueueing rather than calling a provider directly is deliberate.
            // Queued work inherits the attempt limits, policy gate and evidence
            // trail that every CLI-originated task goes through; a direct
            // provider call from an HTTP handler would have none of them.
            responder = QueuedWorkConversationResponder(
                queue = { task -> queueService.enqueue(task).id }
            ),
            work = AgentQueueWorkRunner(queueService, activeProvider),
            // Bound here, not defaulted inside BridgeRoutes, for the same
            // reason the queue is: a test checking one projection must not have
            // to own a goal store on disk. This is what lets a phone ask the
            // engine to build something rather than only watch it.
            selfHost = atropos.core.agent.SelfHostGoalService(repoRoot),
            // The same CommandRouter the terminal builds, rendering into a
            // buffer. This is what makes the phone and the browser equal to the
            // CLI: not a reimplementation of each command, the command itself.
            // PortCommandPolicy decides what may reach it.
            commandRunner = BridgeCommandRunner()::run,
            mcpHost = atropos.core.integration.McpHostManager(
                repoRoot,
                localOnly = AtroposConfig.load().runtime.localOnly
            ),
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
 * Source-compatible name for callers that still use the historical bridge
 * identifier. All behavior remains owned by [LocalEngineBridge].
 */
@Deprecated("Use LocalEngineBridge")
object AtroposBridge {
    fun fromEnvironment(
        environment: (String) -> String? = System::getenv,
        activeProvider: () -> String
    ): EngineHttpServer? = LocalEngineBridge.fromEnvironment(environment, activeProvider)

    fun fromEnvironment(activeProvider: () -> String): EngineHttpServer? =
        LocalEngineBridge.fromEnvironment(activeProvider)

    fun server(
        port: Int,
        governance: GovernanceLedger = GovernanceLedger(),
        activeProvider: () -> String
    ): EngineHttpServer = LocalEngineBridge.server(port, governance, activeProvider)
}
