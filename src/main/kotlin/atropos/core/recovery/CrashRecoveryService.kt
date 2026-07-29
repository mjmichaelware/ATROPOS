package atropos.core.recovery

import atropos.core.AtroposConfig
import atropos.core.AtroposRepoRootLocator
import atropos.core.agent.AgentDaemonService
import atropos.core.agent.AgentQueueRecovery
import atropos.core.agent.AgentQueueService
import atropos.core.agent.AgentQueueStore
import atropos.core.agent.GoalContinuationService
import atropos.core.agent.GoalRunStore
import atropos.core.agent.ProviderSessionSupervisor
import atropos.core.dag.DagExecutionService
import atropos.core.dag.DagStore
import atropos.core.memory.LocalMemoryStore
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant

data class RecoveryReport(
    val recoveredAt: Instant,
    val staleQueueEntries: Int,
    val staleSessions: Int,
    val staleDagClaims: Int,
    val interruptedRuns: Int,
    val completedMutationsSkipped: Int,
    val errors: List<String>,
    val message: String
)

enum class RecoveryOutcome {
    FULL_RECOVERY,
    PARTIAL_RECOVERY,
    RECOVERY_FAILED
}

class CrashRecoveryService(
    private val config: AtroposConfig = AtroposConfig.load(),
    private val repoRoot: Path = AtroposRepoRootLocator.resolve(),
    private val queueService: AgentQueueService = AgentQueueService(config),
    private val queueStore: AgentQueueStore = AgentQueueStore(repoRoot),
    private val queueRecovery: AgentQueueRecovery = AgentQueueRecovery(queueStore),
    private val sessionSupervisor: ProviderSessionSupervisor = ProviderSessionSupervisor(repoRoot),
    private val continuationService: GoalContinuationService = GoalContinuationService(repoRoot),
    private val goalRunStore: GoalRunStore = GoalRunStore(repoRoot),
    private val dagService: DagExecutionService = DagExecutionService(config, repoRoot),
    private val dagStore: DagStore = DagStore(repoRoot),
    private val memoryStore: LocalMemoryStore = LocalMemoryStore(repoRoot.resolve(".atropos/memory").toFile()),
    private val daemonService: AgentDaemonService = AgentDaemonService(config),
    private val clock: () -> Instant = { Instant.now() }
) {
    fun recover(): RecoveryReport {
        val errors = mutableListOf<String>()
        val now = clock()

        // 1. Recover stale queue entries
        var staleQueues = 0
        runCatching {
            val result = queueRecovery.recover()
            staleQueues = result.transitions.size
        }.getOrElse {
            errors.add("queue recovery: ${it.message}")
        }

        // 2. Recover stale sessions
        var staleSessions = 0
        runCatching {
            val dead = sessionSupervisor.detectDeadSessions()
            for (session in dead) {
                sessionSupervisor.recoverStaleSession(session.id)
                staleSessions++
            }
        }.onFailure { errors.add("session recovery: ${it.message}") }

        // 3. Recover stale DAG claims
        var staleDagClaims = 0
        runCatching {
            staleDagClaims = dagService.recoverStaleClaims()
        }.onFailure { errors.add("dag recovery: ${it.message}") }

        // 4. Handle interrupted goal runs
        var interruptedRuns = 0
        runCatching {
            val runs = goalRunStore.listRuns(Int.MAX_VALUE)
            for (run in runs) {
                if (run.status == atropos.core.agent.GoalRunStatus.RUNNING || run.status == atropos.core.agent.GoalRunStatus.CONTINUING) {
                    if (run.lastContinuationAt != null && run.lastContinuationAt.plusSeconds(300).isBefore(now)) {
                        continuationService.markRecoveryRequired(
                            run.id,
                            "interrupted: recovered during crash recovery",
                            listOf(
                                "recovery=crash",
                                "recoveredAt=$now",
                                "continuations=${run.continuationCount}",
                                "phase=${run.activePhase ?: "none"}",
                                "node=${run.currentNodeId ?: "none"}",
                                "checkpoint=${run.lastVerifiedCheckpoint ?: "none"}"
                            )
                        )
                        interruptedRuns++
                    }
                }
            }
        }.onFailure { errors.add("goal run recovery: ${it.message}") }

        // 5. Check for interrupted writes by looking for .tmp files
        var completedMutationsSkipped = 0
        runCatching {
            val tmpFiles = Files.walk(repoRoot.resolve(".atropos"))
                .filter { it.fileName.toString().endsWith(".tmp") }
                .toList()
            for (tmp in tmpFiles) {
                val age = System.currentTimeMillis() - tmp.toFile().lastModified()
                if (age > 300_000) { // older than 5 minutes
                    Files.deleteIfExists(tmp)
                    completedMutationsSkipped++
                }
            }
        }.onFailure { errors.add("tmp file cleanup: ${it.message}") }

        val recovered = RecoveryReport(
            recoveredAt = clock(),
            staleQueueEntries = staleQueues,
            staleSessions = staleSessions,
            staleDagClaims = staleDagClaims,
            interruptedRuns = interruptedRuns,
            completedMutationsSkipped = completedMutationsSkipped,
            errors = errors,
            message = buildString {
                append("recovered: $staleQueues queue entries, $staleSessions sessions, $staleDagClaims DAG claims, $interruptedRuns runs")
                if (errors.isNotEmpty()) append(", ${errors.size} errors")
            }
        )

        memoryStore.rememberDetailed(
            kind = atropos.core.memory.MemoryKind.RECOVERY,
            title = "crash recovery",
            body = buildString {
                appendLine("queues=$staleQueues")
                appendLine("sessions=$staleSessions")
                appendLine("dag=$staleDagClaims")
                appendLine("runs=$interruptedRuns")
                appendLine("tmp=$completedMutationsSkipped")
                if (errors.isNotEmpty()) appendLine("errors=${errors.joinToString("; ")}")
            }.trimEnd(),
            tags = listOf("recovery", if (errors.isEmpty()) "clean" else "errors"),
            subjectType = "recovery",
            subjectId = "recovery-${clock()}"
        )

        return recovered
    }

    fun renderReport(report: RecoveryReport): String = buildString {
        appendLine("Crash Recovery Report")
        appendLine("recovered at: ${report.recoveredAt}")
        appendLine("stale queue entries: ${report.staleQueueEntries}")
        appendLine("stale sessions: ${report.staleSessions}")
        appendLine("stale DAG claims: ${report.staleDagClaims}")
        appendLine("interrupted runs: ${report.interruptedRuns}")
        appendLine("completed mutations skipped: ${report.completedMutationsSkipped}")
        if (report.errors.isNotEmpty()) {
            appendLine("errors:")
            report.errors.forEach { appendLine("  - $it") }
        }
        appendLine("result: ${report.message}")
    }.trimEnd()
}
