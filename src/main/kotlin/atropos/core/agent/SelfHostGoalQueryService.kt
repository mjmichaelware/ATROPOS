package atropos.core.agent

import atropos.core.dag.DagExecutionService
import atropos.core.memory.LocalMemoryStore
import atropos.core.memory.MemoryRecord

/** Read-only self-host goal queries kept separate from mutation orchestration. */
class SelfHostGoalQueryService(
    private val store: GoalRunStore,
    private val dagService: DagExecutionService,
    private val memoryStore: LocalMemoryStore
) {
    private val selector = SelfHostGoalSelector(store)
    private val benchmarkService = SelfHostBenchmarkService(selector)

    fun load(goalId: String): SelfHostResult {
        val record = store.resolve(goalId)
            ?: return SelfHostResult(false, "goal not found: $goalId")
        if (record.isTerminal()) {
            return SelfHostResult(false, "goal already terminal: ${record.terminalCondition}", SelfHostGoal(record, null))
        }
        return SelfHostResult(true, "goal loaded: $goalId", SelfHostGoal(record, record.dagId?.let(dagService::readDag)))
    }

    fun unfinished(): List<SelfHostGoal> = selector.unfinishedSelfHostRuns().map { record ->
        SelfHostGoal(record, record.dagId?.let(dagService::readDag))
    }

    fun resolve(goalId: String?, requireUnfinished: Boolean, operation: String): SelfHostResult {
        val record = selector.resolveSelfHostGoalRecord(goalId, requireUnfinished)
            ?: return SelfHostResult(false, selector.missingSelfHostGoalMessage(goalId, requireUnfinished, operation))
        return SelfHostResult(
            true,
            "${operation} goal selected: ${record.id}",
            SelfHostGoal(record, record.dagId?.let(dagService::readDag))
        )
    }

    fun status(goalId: String?): SelfHostStatus {
        val record = selector.resolveSelfHostGoalRecord(goalId, requireUnfinished = false)
            ?: return SelfHostStatus(
                goalId = goalId ?: "none",
                status = GoalRunStatus.FAILED,
                terminalCondition = GoalTerminalCondition.TERMINAL_FAILURE,
                phase = null,
                currentNodeId = null,
                dagStatus = null,
                message = selector.missingSelfHostGoalMessage(goalId, false, "inspect")
            )
        return SelfHostStatus(
            goalId = record.id,
            status = record.status,
            terminalCondition = record.terminalCondition,
            phase = record.activePhase,
            currentNodeId = record.currentNodeId,
            dagStatus = record.dagId?.let(dagService::status),
            message = "goal ${record.id}: ${record.status}"
        )
    }

    fun nextAction(goalId: String?): SelfHostNextAction {
        val selected = selector.resolveSelfHostGoalRecord(goalId, requireUnfinished = false)
            ?: return SelfHostNextAction(SelfHostNextActionKind.COMPLETE, null, null, "no self-host goals exist")
        val dag = selected.dagId?.let(dagService::readDag)
        val readyNode = dag?.findReadyNodes()?.firstOrNull()
        if (readyNode != null && !selected.isTerminal()) {
            return SelfHostNextAction(SelfHostNextActionKind.ADVANCE_NODE, selected.id, readyNode.id, "ready DAG node")
        }
        if (selected.terminalCondition == GoalTerminalCondition.VERIFIED_COMPLETE) {
            val kind = if (selected.lastVerifiedCheckpoint?.startsWith("jar:") == true) {
                SelfHostNextActionKind.COMPLETE
            } else {
                SelfHostNextActionKind.PROMOTE_JAR
            }
            val reason = if (kind == SelfHostNextActionKind.COMPLETE) {
                "verified jar already promoted"
            } else {
                "source DAG verified; jar promotion boundary"
            }
            return SelfHostNextAction(kind, selected.id, selected.currentNodeId, reason)
        }
        if (selected.terminalCondition == GoalTerminalCondition.EXTERNAL_INPUT_REQUIRED) {
            return SelfHostNextAction(
                SelfHostNextActionKind.WAIT_EXTERNAL_INPUT,
                selected.id,
                selected.currentNodeId,
                selected.failureReason ?: "external input required"
            )
        }
        if (selected.isTerminal()) {
            return SelfHostNextAction(
                SelfHostNextActionKind.COMPLETE,
                selected.id,
                selected.currentNodeId,
                selected.terminalCondition?.name ?: selected.status.name
            )
        }
        return SelfHostNextAction(SelfHostNextActionKind.HARD_STOP, selected.id, selected.currentNodeId, "no ready node on unfinished self-host goal")
    }

    fun history(limit: Int = 20): List<GoalRunRecord> = selector.allSelfHostRuns().take(limit.coerceAtLeast(1))

    fun benchmarkHistory(): List<GoalRunRecord> = benchmarkService.history()

    fun benchmark(): SelfHostBenchmark = benchmarkService.benchmark()

    fun learned(limit: Int): List<MemoryRecord> = memoryStore.findBySubjectTypes(
        subjectTypes = setOf("selfhost_goal", "selfhost_dag_eval"),
        limit = limit
    )
}
