package atropos.core.recovery

import atropos.core.ApiKeys
import atropos.core.AtroposConfig
import atropos.core.LakehouseConfig
import atropos.core.RuntimeConfig
import atropos.core.agent.AgentContextCollector
import atropos.core.agent.AgentDaemonService
import atropos.core.agent.AgentQueueRecovery
import atropos.core.agent.AgentQueueService
import atropos.core.agent.AgentQueueStore
import atropos.core.agent.GoalContinuationService
import atropos.core.agent.GoalRunStatus
import atropos.core.agent.GoalRunStore
import atropos.core.agent.ProviderSessionSupervisor
import atropos.core.dag.DagExecutionService
import atropos.core.dag.DagStore
import atropos.core.memory.LocalMemoryStore
import java.nio.file.Files
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CrashRecoveryServiceTest {
    @Test
    fun renderReport_redacts_error_and_message_secrets() {
        val root = Files.createTempDirectory("atropos-crash-report-redaction-")
        val service = CrashRecoveryService(repoRoot = root)
        val report = RecoveryReport(
            recoveredAt = Instant.EPOCH,
            staleQueueEntries = 0,
            staleSessions = 0,
            staleDagClaims = 0,
            interruptedRuns = 0,
            completedMutationsSkipped = 0,
            errors = listOf("provider token=plain-token"),
            message = "recovery token=plain-token"
        )

        val rendered = service.renderReport(report)

        assertFalse(rendered.contains("plain-token"), rendered)
        assertTrue(rendered.contains("<redacted:secret>"), rendered)
    }

    @Test
    fun recover_marks_stale_goal_runs_as_recovery_required_with_exact_evidence() {
        val repoRoot = Files.createTempDirectory("atropos-crash-recovery-")
        Files.createDirectories(repoRoot.resolve(".atropos"))
        val now = Instant.parse("2026-07-27T07:05:00Z")
        val config = AtroposConfig(
            keys = ApiKeys(groq = "", openai = "", anthropic = "", xai = ""),
            lakehouse = LakehouseConfig(
                mountPath = repoRoot.resolve("lakehouse").toString(),
                dbPath = repoRoot.resolve("lakehouse/vector_storage.db").toString()
            ),
            runtime = RuntimeConfig(defaultProvider = "groq", temperature = 0.2)
        )
        val goalRunStore = GoalRunStore(repoRoot, clock = { now })
        val continuationService = GoalContinuationService(
            repoRoot = repoRoot,
            store = goalRunStore,
            memoryStore = LocalMemoryStore(repoRoot.resolve(".atropos/memory").toFile(), env = emptyMap()),
            clock = { now }
        )
        val created = goalRunStore.createGoalRun("phase 11 crash recovery", provider = "self-host")
        val stale = goalRunStore.update(
            created.copy(
                status = GoalRunStatus.CONTINUING,
                continuationCount = 4,
                lastContinuationAt = now.minusSeconds(301),
                activePhase = "11",
                currentNodeId = "node-9",
                lastVerifiedCheckpoint = "verify-4"
            )
        )

        val queueStore = AgentQueueStore(repoRoot, clock = { now })
        val queueRecovery = AgentQueueRecovery(queueStore, clock = { now })
        val collector = AgentContextCollector(repoRoot = repoRoot)
        val service = CrashRecoveryService(
            config = config,
            repoRoot = repoRoot,
            queueService = AgentQueueService(config, collector),
            queueStore = queueStore,
            queueRecovery = queueRecovery,
            sessionSupervisor = ProviderSessionSupervisor(repoRoot, clock = { now }),
            continuationService = continuationService,
            goalRunStore = goalRunStore,
            dagService = DagExecutionService(config, repoRoot),
            dagStore = DagStore(repoRoot),
            memoryStore = LocalMemoryStore(repoRoot.resolve(".atropos/memory").toFile(), env = emptyMap()),
            daemonService = AgentDaemonService(config, repoRoot),
            clock = { now }
        )

        val report = service.recover()
        val reopened = goalRunStore.resolve(stale.id) ?: error("missing recovered run")

        assertEquals(1, report.interruptedRuns)
        assertEquals(GoalRunStatus.RECOVERY_REQUIRED, reopened.status)
        assertTrue(!reopened.isTerminal())
        assertEquals("interrupted: recovered during crash recovery", reopened.failureReason)
        assertTrue(reopened.evidence.any { it == "recovery=crash" })
        assertTrue(reopened.evidence.any { it == "recoveredAt=$now" })
        assertTrue(reopened.evidence.any { it == "phase=11" })
        assertTrue(reopened.evidence.any { it == "node=node-9" })
        assertTrue(reopened.evidence.any { it == "checkpoint=verify-4" })
    }

    @Test
    fun recover_scans_full_run_history_instead_of_only_latest_window() {
        val repoRoot = Files.createTempDirectory("atropos-crash-recovery-window-")
        Files.createDirectories(repoRoot.resolve(".atropos"))
        val base = Instant.parse("2026-07-27T07:15:00Z")
        var tick = 0L
        val now = base.plusSeconds(500)
        val config = AtroposConfig(
            keys = ApiKeys(groq = "", openai = "", anthropic = "", xai = ""),
            lakehouse = LakehouseConfig(
                mountPath = repoRoot.resolve("lakehouse").toString(),
                dbPath = repoRoot.resolve("lakehouse/vector_storage.db").toString()
            ),
            runtime = RuntimeConfig(defaultProvider = "groq", temperature = 0.2)
        )
        val goalRunStore = GoalRunStore(repoRoot, clock = { base.plusSeconds(tick++) })
        val continuationService = GoalContinuationService(
            repoRoot = repoRoot,
            store = goalRunStore,
            memoryStore = LocalMemoryStore(repoRoot.resolve(".atropos/memory").toFile(), env = emptyMap()),
            clock = { now }
        )
        val staleCreated = goalRunStore.createGoalRun("older stale self-host run", provider = "self-host")
        val stale = goalRunStore.update(
            staleCreated.copy(
                status = GoalRunStatus.CONTINUING,
                continuationCount = 2,
                lastContinuationAt = base,
                activePhase = "11",
                currentNodeId = "node-3",
                lastVerifiedCheckpoint = "verify-2"
            )
        )
        repeat(60) { index ->
            goalRunStore.createGoalRun("newer generic run $index", provider = "codex")
        }

        val queueStore = AgentQueueStore(repoRoot, clock = { now })
        val queueRecovery = AgentQueueRecovery(queueStore, clock = { now })
        val collector = AgentContextCollector(repoRoot = repoRoot)
        val service = CrashRecoveryService(
            config = config,
            repoRoot = repoRoot,
            queueService = AgentQueueService(config, collector),
            queueStore = queueStore,
            queueRecovery = queueRecovery,
            sessionSupervisor = ProviderSessionSupervisor(repoRoot, clock = { now }),
            continuationService = continuationService,
            goalRunStore = goalRunStore,
            dagService = DagExecutionService(config, repoRoot),
            dagStore = DagStore(repoRoot),
            memoryStore = LocalMemoryStore(repoRoot.resolve(".atropos/memory").toFile(), env = emptyMap()),
            daemonService = AgentDaemonService(config, repoRoot),
            clock = { now }
        )

        val report = service.recover()
        val reopened = goalRunStore.resolve(stale.id) ?: error("missing recovered run")

        assertEquals(1, report.interruptedRuns)
        assertEquals(GoalRunStatus.RECOVERY_REQUIRED, reopened.status)
        assertEquals("interrupted: recovered during crash recovery", reopened.failureReason)
        assertTrue(reopened.evidence.any { it == "node=node-3" })
    }
}
