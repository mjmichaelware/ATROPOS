package atropos.core.director

import atropos.core.AtroposRepoRootLocator
import atropos.core.dag.DagExecutionService
import java.nio.file.Path

/**
 * Supervises the canonical execution DAG without becoming another executor.
 *
 * The Director may stop a run on observed drift, while [DagExecutionService]
 * remains the only owner of claims, node execution, territory grants, and DAG
 * state. The result is advisory evidence; it is never a verification verdict.
 */
class DirectorDagSupervisor(
    private val dagExecution: DagExecutionService = DagExecutionService(),
    private val director: DirectorService = DirectorService(),
    private val repoRoot: Path = AtroposRepoRootLocator.resolve()
) {
    fun supervise(
        dagId: String,
        goalId: String? = null,
        territoryIds: List<String> = emptyList(),
        files: List<String> = emptyList()
    ): DirectorDagSupervision {
        val dag = dagExecution.readDag(dagId)
            ?: return DirectorDagSupervision.refused(dagId, "DAG not found: $dagId")

        val drift = director.scanDiffForDrift(
            goalId = goalId,
            territoryId = territoryIds.firstOrNull()
        )
        val before = director.advisoryBeforePromotion(
            goalId = goalId,
            territoryIds = territoryIds,
            files = files
        )
        if (!before.allowed) {
            director.observe(
                kind = ObservationKind.MISSING_GATE,
                severity = DriftSeverity.WARNING,
                source = "director/dag-supervisor",
                details = "DAG supervision refused before execution: ${before.message}",
                files = files,
                goalId = goalId
            )
            return DirectorDagSupervision(
                dagId = dagId,
                label = dag.label,
                allowed = false,
                execution = null,
                driftCount = drift.size,
                blockingObservations = before.blockingObservations.map { it.id },
                message = before.message
            )
        }

        val execution = dagExecution.evaluateDag(dagId)
        val after = director.advisoryBeforePromotion(
            goalId = goalId,
            territoryIds = territoryIds,
            files = files
        )
        val allowed = execution.ok && after.allowed
        director.observe(
            kind = if (allowed) ObservationKind.MEMORY_WATERMARK else ObservationKind.MISSING_GATE,
            severity = if (allowed) DriftSeverity.INFO else DriftSeverity.WARNING,
            source = "director/dag-supervisor",
            details = "DAG=${dagId} execution=${execution.ok} advisory=${after.allowed} root=${repoRoot.fileName}",
            files = files,
            goalId = goalId
        )
        return DirectorDagSupervision(
            dagId = dagId,
            label = dag.label,
            allowed = allowed,
            execution = execution,
            driftCount = drift.size,
            blockingObservations = after.blockingObservations.map { it.id },
            message = if (allowed) {
                "director supervision passed: ${execution.message}"
            } else {
                "director supervision refused: execution=${execution.ok}; advisory=${after.allowed}"
            }
        )
    }
}
