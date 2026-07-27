package atropos.core.planning

import atropos.core.dag.DagDefinition
import atropos.core.dag.DagNode
import atropos.core.dag.DagNodeAction
import atropos.core.dag.DagNodeState
import java.nio.file.Path
import java.time.Instant

class InternalExecutionDagSynthesizer(
    private val readinessCalculator: InternalReadinessCalculator = InternalReadinessCalculator()
) {
    fun synthesize(projectId: String, label: String, authorityGraph: AuthorityGraph, repoRoot: Path): DagDefinition {
        val now = Instant.now()
        val nodes = authorityGraph.atoms.map { atom ->
            DagNode(
                id = atom.id,
                label = atom.dimension.name.lowercase() + ": " + atom.sectionId,
                dependencies = atom.dependencies.filter { dependency -> authorityGraph.atoms.any { it.id == dependency } },
                territory = atom.territory,
                action = actionFor(atom.dimension),
                actionPayload = atom.statement,
                state = DagNodeState.PENDING,
                createdAt = now,
                updatedAt = now,
                metaFile = repoRoot.resolve(".atropos/dag/execution/definitions/pending-${atom.id}.meta")
            )
        }
        readinessCalculator.rejectCycles(nodes)
        return DagDefinition(
            id = "planned-${authorityGraph.projectId}",
            label = label,
            projectId = projectId,
            nodes = nodes,
            createdAt = now,
            updatedAt = now,
            metaFile = repoRoot.resolve(".atropos/dag/execution/definitions/pending.meta")
        )
    }

    private fun actionFor(dimension: AtomDimension): DagNodeAction =
        when (dimension) {
            AtomDimension.TESTS_ACCEPTANCE -> DagNodeAction.VERIFY
            AtomDimension.SECURITY_SECRETS -> DagNodeAction.SECRET_CHECK
            AtomDimension.TERRITORY_CAPABILITIES -> DagNodeAction.TERRITORY_CHECK
            AtomDimension.OBSERVABILITY_PROVENANCE -> DagNodeAction.POLICY_CHECK
            AtomDimension.ROLLBACK_FAILURE_EVIDENCE -> DagNodeAction.VERIFY
            else -> DagNodeAction.RUN_COMMAND
        }
}
