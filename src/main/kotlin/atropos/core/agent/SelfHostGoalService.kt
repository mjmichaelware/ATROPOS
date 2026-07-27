package atropos.core.agent

import atropos.core.dag.DagDefinition
import atropos.core.dag.DagExecutionService
import atropos.core.dag.DagNodeState
import atropos.core.dag.DagStatus
import atropos.core.memory.LocalMemoryStore
import atropos.core.security.RedactionFilter
import atropos.core.verification.VerifiedCompletionGate
import atropos.core.worktree.IsolatedWorktreeService
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

class SelfHostGoalService(
    private val repoRoot: Path = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize(),
    private val store: GoalRunStore = GoalRunStore(repoRoot),
    private val continuationService: GoalContinuationService = GoalContinuationService(repoRoot),
    private val dagService: DagExecutionService = DagExecutionService(repoRoot = repoRoot),
    private val worktreeService: IsolatedWorktreeService = IsolatedWorktreeService(repoRoot),
    private val completionGate: VerifiedCompletionGate = VerifiedCompletionGate(repoRoot = repoRoot),
    private val memoryStore: LocalMemoryStore = LocalMemoryStore(repoRoot.resolve(".atropos/memory").toFile()),
    private val clock: () -> Instant = { Instant.now() }
) {
    private val selfHostDir = repoRoot.resolve(".atropos/self-hosting").normalize()
    private val runsDir = selfHostDir.resolve("runs")

    fun startGoal(goalName: String, phase: String): SelfHostResult {
        try {
            Files.createDirectories(runsDir)

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
            val metaFile = runsDir.resolve("$goalId.meta")
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

            Files.writeString(metaFile, "", StandardCharsets.UTF_8)
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
        return store.listRuns(50)
            .filter { !it.isTerminal() && it.provider == "self-host" }
            .mapNotNull { record ->
                val dag = record.dagId?.let { dagService.readDag(it) }
                SelfHostGoal(record, dag)
            }
    }

    fun status(goalId: String? = null): SelfHostStatus {
        val record = if (goalId != null) {
            store.resolve(goalId)
        } else {
            store.listRuns(50).firstOrNull { !it.isTerminal() && it.provider == "self-host" }
                ?: store.listRuns(1).firstOrNull()
        } ?: return SelfHostStatus("none", GoalRunStatus.FAILED, GoalTerminalCondition.TERMINAL_FAILURE, null, null, null, "no self-host goals found")

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
                if (failedCount > 0 || blockedCount > 0) {
                    completeGoal(goalId, GoalTerminalCondition.TERMINAL_FAILURE, "$failedCount failed, $blockedCount blocked nodes")
                } else {
                    completeGoal(goalId, GoalTerminalCondition.VERIFIED_COMPLETE, "all nodes complete")
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
        return store.listRuns(limit).filter { it.provider == "self-host" }
    }

    private fun fingerprint(text: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(text.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return digest.take(16)
    }
}
