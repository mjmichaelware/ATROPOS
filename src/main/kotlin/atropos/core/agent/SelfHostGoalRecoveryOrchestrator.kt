package atropos.core.agent

import atropos.core.dag.DagExecutionService
import atropos.core.recovery.RestartCoordinator

class SelfHostGoalRecoveryOrchestrator(
    private val store: GoalRunStore,
    private val dagService: DagExecutionService,
    private val restartCoordinator: RestartCoordinator,
    private val goalQueries: SelfHostGoalQueryService
) {
    fun recoverAndContinue(goalService: SelfHostGoalService, goalId: String? = null, compactState: String? = "self-host restart recovery"): SelfHostResult {
        val recovered = restartCoordinator.recoverAndSnapshot()
        val selected = goalService.resolveResumableGoal(goalId)
        if (!selected.ok) return SelfHostResult(false, "${recovered.message}; ${selected.message}", selected.goal)
        val record = selected.goal?.record ?: return SelfHostResult(false, "${recovered.message}; no resumable self-host goal selected")
        if (!recovered.ok) {
            val recorded = goalService.addEvidence(record.id, "restart_recovery_stop goal=${record.id} reason=${recovered.message}")
            return SelfHostResult(false, "restart recovery refused continuation: ${recovered.message}", recorded.goal ?: selected.goal)
        }
        val restoredNode = recovered.restoredNodes.firstOrNull { it.restored && it.dagId == record.dagId }
        val nextAction = goalService.planNextAction(record.id)
        val evidence = listOf(
            "restart_snapshot id=${recovered.snapshot.id} goals=${recovered.snapshot.goalRuns.size} dags=${recovered.snapshot.dags.size}",
            "restart_recovery ok=${recovered.ok} restored=${recovered.restoredNodes.count { it.restored }} blocked=${recovered.restoredNodes.count { !it.restored }}",
            "restart_next goal=${record.id} node=${nextAction.nodeId ?: restoredNode?.nodeId ?: "none"}",
            nextAction.evidenceLine()
        )
        store.update(record.copy(evidence = record.evidence + evidence))
        return when (nextAction.kind) {
            SelfHostNextActionKind.ADVANCE_NODE, SelfHostNextActionKind.ADVANCE_GOAL -> goalService.advanceNextResumableGoal(record.id, compactState)
            SelfHostNextActionKind.PROMOTE_JAR -> SelfHostResult(false, "restart continuation stopped at promotion boundary: ${nextAction.reason}", SelfHostGoal(record, record.dagId?.let { dagService.readDag(it) }))
            SelfHostNextActionKind.WAIT_EXTERNAL_INPUT, SelfHostNextActionKind.HARD_STOP -> SelfHostResult(false, "restart continuation stopped: ${nextAction.reason}", SelfHostGoal(record, record.dagId?.let { dagService.readDag(it) }))
            SelfHostNextActionKind.COMPLETE -> SelfHostResult(true, "restart continuation complete: ${nextAction.reason}", SelfHostGoal(record, record.dagId?.let { dagService.readDag(it) }))
        }
    }
}
