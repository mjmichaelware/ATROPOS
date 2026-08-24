package atropos.core.factory

import atropos.core.dag.DagNode

data class FactoryRepairResult(
    val evidence: String,
    val loop: FactoryLoopResult
)

/** The only repair re-entry seam: repair evidence is checked by the frozen oracle before resuming the DAG. */
class FactoryRepairExecutor(
    private val obligationLoop: FactoryObligationLoop
) {
    fun repairAndResume(
        handoff: FactoryRunHandoffState,
        freeze: FactoryAcceptanceFreeze,
        repair: () -> FactoryAcceptanceFreeze.RepairEvidence,
        executeWave: (List<DagNode>) -> Set<String>
    ): FactoryRepairResult {
        val evidence = obligationLoop.recordRepairEvidence(freeze, repair())
        val resumed = obligationLoop.resume(handoff, freeze, executeWave)
        return FactoryRepairResult(evidence, resumed)
    }
}
