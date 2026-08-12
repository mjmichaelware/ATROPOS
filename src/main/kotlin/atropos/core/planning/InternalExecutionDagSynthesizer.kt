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
            val lineage = listOf(
                "source_document_id=${atom.documentId}",
                "source_section_id=${atom.sectionId}",
                "source_coordinates=${atom.sourceCoordinates}",
                atom.promptFingerprint.takeIf { it.isNotBlank() }?.let { "prompt_fingerprint=$it" },
                atom.promptSpans.takeIf { it.isNotBlank() }?.let { "prompt_spans=$it" },
                atom.sourceDocumentSha256.takeIf { it.isNotBlank() }?.let { "source_document_sha256=$it" }
            ).filterNotNull().joinToString("\n")
            DagNode(
                id = atom.id,
                label = atom.dimension.name.lowercase() + ": " + atom.sectionId,
                dependencies = atom.dependencies.filter { dependency -> authorityGraph.atoms.any { it.id == dependency } },
                territory = atom.territory,
                action = actionFor(atom.dimension),
                actionPayload = listOf(atom.statement, lineage).filter { it.isNotBlank() }.joinToString("\n"),
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

    /**
     * The action an atom's dimension implies.
     *
     * Five dimensions are checks and map to a checking action. Everything else
     * describes work that produces code, and those used to map to
     * [DagNodeAction.RUN_COMMAND] with the atom's *English statement* as the
     * payload — so the executor was handed a sentence to run as a shell
     * command. [atropos.core.policy.BoundedProcessRunner] refused it, correctly:
     * that refusal is `P(raw-prose-execution)=0` working exactly as designed.
     * The defect was upstream, in handing it prose at all.
     *
     * A code-writing atom is now [DagNodeAction.PROVIDER_CALL], whose executor
     * already exists and whose payload is *meant* to be a statement of intent.
     * Nothing here writes a file: the provider call produces content and the
     * dependent mutation node writes it, which keeps generation and mutation on
     * opposite sides of the gate rather than fused into one unreviewable step.
     */
    private fun actionFor(dimension: AtomDimension): DagNodeAction =
        when (dimension) {
            AtomDimension.TESTS_ACCEPTANCE -> DagNodeAction.VERIFY
            AtomDimension.SECURITY_SECRETS -> DagNodeAction.SECRET_CHECK
            AtomDimension.TERRITORY_CAPABILITIES -> DagNodeAction.TERRITORY_CHECK
            AtomDimension.OBSERVABILITY_PROVENANCE -> DagNodeAction.POLICY_CHECK
            AtomDimension.ROLLBACK_FAILURE_EVIDENCE -> DagNodeAction.VERIFY
            else -> DagNodeAction.PROVIDER_CALL
        }
}
