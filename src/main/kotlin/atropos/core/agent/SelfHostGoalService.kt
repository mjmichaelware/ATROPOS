package atropos.core.agent

import atropos.core.dag.DagDefinition
import atropos.core.dag.DagExecutionService
import atropos.core.dag.DagNodeState
import atropos.core.dag.DagStatus
import atropos.core.memory.MemoryRecord
import atropos.core.memory.LocalMemoryStore
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID

data class SelfHostGoal(
    val record: GoalRunRecord,
    val dag: DagDefinition?
)

data class SelfHostResult(
    val ok: Boolean,
    val message: String,
    val goal: SelfHostGoal? = null
)

data class SelfHostStatus(
    val goalId: String,
    val status: GoalRunStatus,
    val terminalCondition: GoalTerminalCondition?,
    val phase: String?,
    val currentNodeId: String?,
    val dagStatus: DagStatus?,
    val message: String
)

data class SelfHostBenchmark(
    val totalGoals: Int,
    val completed: Int,
    val failed: Int,
    val cancelled: Int,
    val recoveryRequired: Int,
    val totalContinuations: Int,
    val avgContinuations: Double,
    val status: String
)

class SelfHostGoalService(
    private val repoRoot: Path = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize(),
    private val store: GoalRunStore = GoalRunStore(repoRoot),
    private val continuationService: GoalContinuationService = GoalContinuationService(repoRoot),
    private val dagService: DagExecutionService = DagExecutionService(repoRoot = repoRoot),
    private val memoryStore: LocalMemoryStore = LocalMemoryStore(repoRoot.resolve(".atropos/memory").toFile()),
    private val clock: () -> Instant = { Instant.now() }
) {
    fun startGoal(goalName: String, phase: String): SelfHostResult {
        try {
            Files.createDirectories(store.runsRoot())

            val baselineCommit = runCatching {
                val proc = ProcessBuilder("git", "rev-parse", "HEAD")
                    .directory(repoRoot.toFile())
                    .redirectErrorStream(true)
                    .start()
                proc.inputStream.bufferedReader().readText().trim()
            }.getOrNull()

            val dirtyFingerprint = runCatching {
                val proc = ProcessBuilder("git", "status", "--porcelain")
                    .directory(repoRoot.toFile())
                    .redirectErrorStream(true)
                    .start()
                val status = proc.inputStream.bufferedReader().readText()
                fingerprint(status)
            }.getOrNull()

            val goalId = "shg-" + UUID.randomUUID().toString().take(12)
            val now = clock()
            val metaFile = store.runsRoot().resolve("$goalId.meta")
            val record = GoalRunRecord(
                id = goalId,
                goalId = goalId,
                task = goalName.trim(),
                provider = "self-host",
                status = GoalRunStatus.RUNNING,
                baselineCommit = baselineCommit,
                dirtyStateFingerprint = dirtyFingerprint,
                activePhase = phase,
                createdAt = now,
                updatedAt = now,
                metaFile = metaFile
            )

            store.update(record)

            memoryStore.rememberDetailed(
                kind = atropos.core.memory.MemoryKind.SESSION,
                title = "self-host goal started: $goalName",
                body = "phase=$phase baseline=${baselineCommit?.take(12)}",
                tags = listOf("selfhost", "goal", "started"),
                subjectType = "selfhost_goal",
                subjectId = goalId
            )

            return SelfHostResult(true, "self-host goal started: $goalId", SelfHostGoal(record, null))
        } catch (e: Exception) {
            return SelfHostResult(false, "failed to start self-host goal: ${e.message}")
        }
    }

    fun loadGoal(goalId: String): SelfHostResult {
        val record = store.resolve(goalId)
            ?: return SelfHostResult(false, "goal not found: $goalId")
        if (record.isTerminal()) {
            return SelfHostResult(false, "goal already terminal: ${record.terminalCondition}", SelfHostGoal(record, null))
        }
        val dag = record.dagId?.let { dagService.readDag(it) }
        return SelfHostResult(true, "goal loaded: $goalId", SelfHostGoal(record, dag))
    }

    fun loadUnfinishedGoals(): List<SelfHostGoal> {
        return unfinishedSelfHostRuns()
            .mapNotNull { record ->
                val dag = record.dagId?.let { dagService.readDag(it) }
                SelfHostGoal(record, dag)
            }
    }

    fun resolveResumableGoal(goalId: String? = null): SelfHostResult {
        val record = resolveSelfHostGoalRecord(
            goalId = goalId,
            requireUnfinished = true
        ) ?: return SelfHostResult(false, missingSelfHostGoalMessage(goalId, requireUnfinished = true, operation = "resume"))
        val dag = record.dagId?.let { dagService.readDag(it) }
        return SelfHostResult(true, "resumable goal selected: ${record.id}", SelfHostGoal(record, dag))
    }

    fun resolveWatchGoal(goalId: String? = null): SelfHostResult {
        val record = resolveSelfHostGoalRecord(
            goalId = goalId,
            requireUnfinished = false
        ) ?: return SelfHostResult(false, missingSelfHostGoalMessage(goalId, requireUnfinished = false, operation = "watch"))
        val dag = record.dagId?.let { dagService.readDag(it) }
        return SelfHostResult(true, "watch goal selected: ${record.id}", SelfHostGoal(record, dag))
    }

    fun resolveStatusGoal(goalId: String? = null): SelfHostResult {
        val record = resolveSelfHostGoalRecord(
            goalId = goalId,
            requireUnfinished = false
        ) ?: return SelfHostResult(false, missingSelfHostGoalMessage(goalId, requireUnfinished = false, operation = "inspect"))
        val dag = record.dagId?.let { dagService.readDag(it) }
        return SelfHostResult(true, "status goal selected: ${record.id}", SelfHostGoal(record, dag))
    }

    fun resolveStoppableGoal(goalId: String? = null): SelfHostResult {
        val record = resolveSelfHostGoalRecord(
            goalId = goalId,
            requireUnfinished = true
        ) ?: return SelfHostResult(false, missingSelfHostGoalMessage(goalId, requireUnfinished = true, operation = "stop"))
        val dag = record.dagId?.let { dagService.readDag(it) }
        return SelfHostResult(true, "stoppable goal selected: ${record.id}", SelfHostGoal(record, dag))
    }

    fun status(goalId: String? = null): SelfHostStatus {
        val record = resolveSelfHostGoalRecord(
            goalId = goalId,
            requireUnfinished = false
        ) ?: return SelfHostStatus(
            goalId = goalId ?: "none",
            status = GoalRunStatus.FAILED,
            terminalCondition = GoalTerminalCondition.TERMINAL_FAILURE,
            phase = null,
            currentNodeId = null,
            dagStatus = null,
            message = missingSelfHostGoalMessage(goalId, requireUnfinished = false, operation = "inspect")
        )

        val dagStatus = record.dagId?.let { dagService.status(it) }
        return SelfHostStatus(
            goalId = record.id,
            status = record.status,
            terminalCondition = record.terminalCondition,
            phase = record.activePhase,
            currentNodeId = record.currentNodeId,
            dagStatus = dagStatus,
            message = "goal ${record.id}: ${record.status}"
        )
    }

    fun updatePhase(goalId: String, phase: String): SelfHostResult {
        val record = store.resolve(goalId)
            ?: return SelfHostResult(false, "goal not found: $goalId")
        val updated = store.update(record.copy(activePhase = phase))
        return SelfHostResult(true, "phase updated to $phase", SelfHostGoal(updated, null))
    }

    fun updateCurrentNode(goalId: String, nodeId: String): SelfHostResult {
        val record = store.resolve(goalId)
            ?: return SelfHostResult(false, "goal not found: $goalId")
        val updated = store.update(record.copy(currentNodeId = nodeId))
        return SelfHostResult(true, "current node set to $nodeId", SelfHostGoal(updated, null))
    }

    fun setDag(goalId: String, dagId: String): SelfHostResult {
        val record = store.resolve(goalId)
            ?: return SelfHostResult(false, "goal not found: $goalId")
        val dag = dagService.readDag(dagId)
            ?: return SelfHostResult(false, "DAG not found: $dagId")
        val updated = store.update(record.copy(dagId = dagId))
        return SelfHostResult(true, "DAG set to $dagId", SelfHostGoal(updated, dag))
    }

    fun addEvidence(goalId: String, evidenceEntry: String): SelfHostResult {
        val record = store.resolve(goalId)
            ?: return SelfHostResult(false, "goal not found: $goalId")
        val updated = store.update(record.copy(
            evidence = record.evidence + evidenceEntry
        ))
        return SelfHostResult(true, "evidence added", SelfHostGoal(updated, null))
    }

    fun setTerritory(goalId: String, territory: List<String>): SelfHostResult {
        val record = store.resolve(goalId)
            ?: return SelfHostResult(false, "goal not found: $goalId")
        val updated = store.update(record.copy(territory = territory))
        return SelfHostResult(true, "territory set", SelfHostGoal(updated, null))
    }

    fun updateVerifiedCheckpoint(goalId: String, checkpoint: String): SelfHostResult {
        val record = store.resolve(goalId)
            ?: return SelfHostResult(false, "goal not found: $goalId")
        val updated = store.update(record.copy(lastVerifiedCheckpoint = checkpoint))
        return SelfHostResult(true, "checkpoint updated to $checkpoint", SelfHostGoal(updated, null))
    }

    fun completeGoal(goalId: String, condition: GoalTerminalCondition, reason: String? = null): SelfHostResult {
        val result = continuationService.completeRun(goalId, condition, reason)
        if (!result.ok) return SelfHostResult(false, result.message)
        val record = result.record ?: return SelfHostResult(false, "goal not found after completion")
        memoryStore.rememberDetailed(
            kind = atropos.core.memory.MemoryKind.SESSION,
            title = "self-host goal completed: $condition",
            body = "goal=$goalId reason=${reason ?: "none"}",
            tags = listOf("selfhost", "goal", condition.name.lowercase()),
            subjectType = "selfhost_goal",
            subjectId = goalId
        )
        return SelfHostResult(true, "goal completed: $condition", SelfHostGoal(record, null))
    }

    fun resumeGoal(goalId: String, compactState: String? = null): SelfHostResult {
        val record = store.resolve(goalId)
            ?: return SelfHostResult(false, "goal not found: $goalId")
        if (record.isTerminal()) {
            return SelfHostResult(false, "goal already terminal: ${record.terminalCondition}", SelfHostGoal(record, null))
        }
        val resumed = continuationService.continueRun(
            record.id,
            GoalContinuationRequest(
                goalRunId = record.id,
                compactState = compactState,
                continuationIndex = record.continuationCount + 1,
                lastResponseSummary = null,
                provider = record.provider ?: "self-host"
            )
        )
        if (!resumed.ok) {
            return SelfHostResult(false, resumed.message, resumed.record?.let { SelfHostGoal(it, null) })
        }
        val updated = resumed.record ?: return SelfHostResult(false, "goal not found after resume")
        val dag = updated.dagId?.let { dagService.readDag(it) }
        return SelfHostResult(true, resumed.message, SelfHostGoal(updated, dag))
    }

    fun selectNextDagNode(goalId: String): SelfHostResult {
        val record = store.resolve(goalId)
            ?: return SelfHostResult(false, "goal not found: $goalId")
        val dagId = record.dagId ?: return SelfHostResult(false, "no DAG assigned to goal")
        val dag = dagService.readDag(dagId) ?: return SelfHostResult(false, "DAG not found: $dagId")

        val dagStatus = dagService.status(dagId) ?: return SelfHostResult(false, "unable to get DAG status")
        val readyNodes = dagStatus.readyNodes

        if (readyNodes.isEmpty()) {
            val allTerminal = dag.nodes.all { it.state.terminal }
            if (allTerminal) {
                val failedCount = dag.nodes.count { it.state == DagNodeState.FAILED }
                val blockedCount = dag.nodes.count { it.state == DagNodeState.BLOCKED }
                val completed = if (failedCount > 0 || blockedCount > 0) {
                    completeGoal(goalId, GoalTerminalCondition.TERMINAL_FAILURE, "$failedCount failed, $blockedCount blocked nodes")
                } else {
                    // Verified completion has to be backed by evidence, so the
                    // loop records what it actually observed — which nodes
                    // terminated and how — before it claims the goal is proven
                    // done. Previously it asserted verified completion having
                    // written nothing down.
                    addEvidence(
                        goalId,
                        "dag=$dagId nodes=${dag.nodes.size} complete=" +
                            dag.nodes.count { it.state == DagNodeState.COMPLETE } +
                            " not-applicable=" + dag.nodes.count { it.state == DagNodeState.NOT_APPLICABLE }
                    )
                    completeGoal(goalId, GoalTerminalCondition.VERIFIED_COMPLETE, "all nodes complete")
                }
                if (completed.ok) {
                    return SelfHostResult(false, "no ready nodes in DAG $dagId", SelfHostGoal(completed.goal?.record ?: record, dag))
                }
            }
            return SelfHostResult(false, "no ready nodes in DAG $dagId", SelfHostGoal(record, dag))
        }

        val nextNodeId = readyNodes.first()
        val updated = store.update(record.copy(currentNodeId = nextNodeId))
        return SelfHostResult(true, "selected next node: $nextNodeId", SelfHostGoal(updated, dag))
    }

    fun evaluateReadyDagNode(goalId: String): SelfHostResult {
        val record = store.resolve(goalId)
            ?: return SelfHostResult(false, "goal not found: $goalId")
        val currentNodeId = record.currentNodeId ?: return SelfHostResult(false, "no current node selected")
        val dagId = record.dagId ?: return SelfHostResult(false, "no DAG assigned")

        val result = dagService.evaluateDag(dagId)
        val dag = dagService.readDag(dagId)

        // Record experience from this evaluation
        val experienceBody = buildString {
            appendLine("goal: $goalId")
            appendLine("phase: ${record.activePhase ?: "unknown"}")
            val totalNodes = result.completedNodes + result.failedNodes + result.blockedNodes + (result.nodeResults?.size ?: 0).coerceAtMost(0)
            appendLine("DAG: ${result.completedNodes}/${totalNodes.coerceAtLeast(result.completedNodes)} completed, ${result.failedNodes} failed, ${result.blockedNodes} blocked")
            appendLine("message: ${result.message}")
            result.nodeResults?.forEach { nodeResult ->
                appendLine("  node ${nodeResult.nodeId}: ${nodeResult.ok} ${nodeResult.message.take(80)}")
            }
        }
        memoryStore.rememberDetailed(
            kind = atropos.core.memory.MemoryKind.BATCH,
            title = "self-host DAG evaluation: ${record.activePhase ?: goalId}",
            body = experienceBody.toString(),
            tags = listOf("selfhost", "dag", "evaluation", if (result.ok) "success" else "failure"),
            subjectType = "selfhost_dag_eval",
            subjectId = goalId
        )

        return SelfHostResult(result.ok, "DAG evaluation: ${result.message}", SelfHostGoal(record, dag))
    }

    fun history(limit: Int = 20): List<GoalRunRecord> {
        return allSelfHostRuns().take(limit.coerceAtLeast(1))
    }

    fun benchmarkHistory(): List<GoalRunRecord> = allSelfHostRuns()

    fun benchmark(): SelfHostBenchmark {
        val goals = benchmarkHistory()
        val completed = goals.count { it.terminalCondition == GoalTerminalCondition.VERIFIED_COMPLETE }
        val failed = goals.count { it.terminalCondition == GoalTerminalCondition.TERMINAL_FAILURE }
        val cancelled = goals.count { it.terminalCondition == GoalTerminalCondition.CANCELLED }
        val recoveryRequired = goals.count { it.status == GoalRunStatus.RECOVERY_REQUIRED }
        val totalContinuations = goals.sumOf { it.continuationCount }
        val avgContinuations = if (goals.isNotEmpty()) totalContinuations.toDouble() / goals.size else 0.0
        val status = when {
            completed == 0 -> "NOT_ACHIEVED"
            failed > 0 || cancelled > 0 || recoveryRequired > 0 -> "PARTIAL_EVIDENCE"
            else -> "NOMINAL_BATCH_PROVEN"
        }
        return SelfHostBenchmark(
            totalGoals = goals.size,
            completed = completed,
            failed = failed,
            cancelled = cancelled,
            recoveryRequired = recoveryRequired,
            totalContinuations = totalContinuations,
            avgContinuations = avgContinuations,
            status = status
        )
    }

    fun learned(limit: Int = 20): List<MemoryRecord> {
        return memoryStore.findBySubjectTypes(
            subjectTypes = setOf("selfhost_goal", "selfhost_dag_eval"),
            limit = limit
        )
    }

    private fun fingerprint(text: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(text.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return digest.take(16)
    }

    private fun unfinishedSelfHostRuns(): List<GoalRunRecord> =
        allSelfHostRuns()
            .filter { !it.isTerminal() }
            .sortedWith(
                compareByDescending<GoalRunRecord> { it.status == GoalRunStatus.RECOVERY_REQUIRED }
                    .thenByDescending { it.updatedAt }
            )

    private fun allSelfHostRuns(): List<GoalRunRecord> =
        store.listRuns(Int.MAX_VALUE)
            .filter { it.provider == "self-host" }
            .sortedByDescending { it.updatedAt }

    private fun resolveSelfHostGoalRecord(
        goalId: String?,
        requireUnfinished: Boolean
    ): GoalRunRecord? {
        if (!goalId.isNullOrBlank()) {
            val resolved = store.resolve(goalId) ?: return null
            if (resolved.provider != "self-host") return null
            if (requireUnfinished && resolved.isTerminal()) return null
            return resolved
        }
        return if (requireUnfinished) {
            unfinishedSelfHostRuns().firstOrNull()
        } else {
            unfinishedSelfHostRuns().firstOrNull()
                ?: allSelfHostRuns().firstOrNull()
        } ?: run {
            null
        }
    }

    private fun missingSelfHostGoalMessage(goalId: String?, requireUnfinished: Boolean, operation: String): String {
        if (!goalId.isNullOrBlank()) {
            val resolved = store.resolve(goalId) ?: return "goal not found: $goalId"
            if (resolved.provider != "self-host") return "goal is not self-host managed: $goalId"
            if (requireUnfinished && resolved.isTerminal()) {
                return "goal already terminal: ${resolved.terminalCondition}"
            }
            return "unable to $operation goal: $goalId"
        }
        return if (requireUnfinished) {
            "no unfinished self-host goals to $operation"
        } else {
            "no self-host goals to $operation"
        }
    }
}
