package atropos.core.director

import atropos.core.AtroposRepoRootLocator
import atropos.core.dag.DagExecutionService
import atropos.core.territory.TerritoryAssignment
import atropos.core.territory.TerritoryEnforcer
import java.nio.file.Path

/**
 * Supervises the canonical execution DAG without becoming another executor.
 *
 * The Director may stop a run on observed drift, while [DagExecutionService]
 * remains the only owner of claims, node execution, territory grants, and DAG
 * state. The result is advisory evidence; it is never a verification verdict.
 */
class DirectorDagSupervisor(
    private val repoRoot: Path = AtroposRepoRootLocator.resolve(),
    private val dagExecution: DagExecutionService = DagExecutionService(repoRoot = repoRoot),
    private val director: DirectorService = DirectorService(DirectorStore(repoRoot), repoRoot)
) {
    fun supervise(
        dagId: String,
        goalId: String? = null,
        territoryIds: List<String> = emptyList(),
        files: List<String> = emptyList()
    ): DirectorDagSupervision {
        val dag = dagExecution.readDag(dagId)
            ?: return DirectorDagSupervision.refused(dagId, "DAG not found: $dagId")
        val effectiveTerritoryIds = territoryIds.ifEmpty {
            dag.nodes.flatMap { it.territory }.distinct()
        }
        val effectiveFiles = files.ifEmpty {
            dag.nodes.flatMap { it.expectedOutputs }.distinct()
        }
        if (effectiveTerritoryIds.isEmpty()) {
            return DirectorDagSupervision.refused(dagId, "DAG has no declared territory")
        }
        val outsideTerritory = TerritoryEnforcer(effectiveTerritoryIds).firstOutside(effectiveFiles)
        if (outsideTerritory != null) {
            return DirectorDagSupervision.refused(
                dagId,
                "DAG output is outside declared territory: $outsideTerritory"
            )
        }

        val drift = director.scanDiffForDrift(
            territories = effectiveTerritoryIds.map { territory ->
                TerritoryAssignment(
                    id = territory,
                    ownerId = goalId ?: "director-$dagId",
                    ownerRole = "DIRECTOR",
                    allowedPrefix = territory
                )
            },
            goalId = goalId,
            territoryId = effectiveTerritoryIds.firstOrNull()
        )
        val currentBlockingDrift = drift.filter { observation ->
            observation.severity == DriftSeverity.CRITICAL ||
                observation.kind in setOf(
                    ObservationKind.TERRITORY_VIOLATION,
                    ObservationKind.POLICY_VIOLATION,
                    ObservationKind.MISSING_GATE
                )
        }
        if (currentBlockingDrift.isNotEmpty()) {
            val message = "DAG supervision refused on current drift: " +
                currentBlockingDrift.joinToString("; ") { it.details }
            return DirectorDagSupervision(
                dagId = dagId,
                label = dag.label,
                allowed = false,
                execution = null,
                driftCount = drift.size,
                blockingObservations = currentBlockingDrift.map { it.id },
                message = message
            )
        }
        val before = director.advisoryBeforePromotion(
            goalId = goalId,
            territoryIds = effectiveTerritoryIds,
            files = effectiveFiles
        )
        if (!before.allowed) {
            director.observe(
                kind = ObservationKind.MISSING_GATE,
                severity = DriftSeverity.WARNING,
                source = "director/dag-supervisor",
                details = "DAG supervision refused before execution: ${before.message}",
                files = effectiveFiles,
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
            territoryIds = effectiveTerritoryIds,
            files = effectiveFiles
        )
        val allowed = execution.ok && after.allowed
        director.observe(
            kind = if (allowed) ObservationKind.MEMORY_WATERMARK else ObservationKind.MISSING_GATE,
            severity = if (allowed) DriftSeverity.INFO else DriftSeverity.WARNING,
            source = "director/dag-supervisor",
            details = "DAG=${dagId} execution=${execution.ok} advisory=${after.allowed} root=${repoRoot.fileName}",
            files = effectiveFiles,
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
