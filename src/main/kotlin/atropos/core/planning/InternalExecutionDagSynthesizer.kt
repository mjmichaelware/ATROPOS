package atropos.core.planning

import atropos.core.dag.DagDefinition
import atropos.core.dag.DagNode
import atropos.core.dag.DagNodeAction
import atropos.core.dag.DagNodeState
import java.nio.file.Path
import java.time.Instant

class InternalExecutionDagSynthesizer(
    private val readinessCalculator: InternalReadinessCalculator = InternalReadinessCalculator(),
    /**
     * Context retrieved for each atom, attached to the node that will execute
     * it. Defaults to [AtomContextProvider.NONE] so a repository with no
     * lakehouse mounted plans exactly as before.
     */
    private val atomContext: AtomContextProvider = AtomContextProvider.NONE
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
                actionPayload = listOf(atom.statement, lineage, contextBlock(atom))
                    .filter { it.isNotBlank() }
                    .joinToString("\n"),
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
     * The atom's retrieved context, as payload text.
     *
     * Attached to the node rather than resolved at execution time, so the plan
     * is a complete statement of what each step was given. Resolving during
     * execution would let two runs of the same plan see different context and
     * still call themselves the same plan.
     *
     * Misses are included. An atom that asked the lakehouse and got nothing
     * should say so on the node, because a generated result that looks
     * uninformed otherwise leaves nobody able to tell whether the shelf was
     * empty or never consulted.
     */
    private fun contextBlock(atom: InternalAtom): String {
        val contexts = runCatching { atomContext.contextFor(atom) }.getOrDefault(emptyList())

        // Narrated, not only embedded. The context was being attached to the
        // node payload and reported nowhere, so an operator had no way to tell
        // whether the lakehouse had been consulted, had missed, or had never
        // been reachable -- three different situations that all looked
        // identical from outside.
        if (contexts.isEmpty()) {
            atropos.core.thinking.Thinking.stream.emit(
                atropos.core.thinking.ThinkingDepth.L3,
                "lakehouse atom=${atom.id.take(8)} no shelf matched"
            )
            return ""
        }
        contexts.forEach { context ->
            atropos.core.thinking.Thinking.stream.emit(
                atropos.core.thinking.ThinkingDepth.L2,
                "lakehouse atom=${atom.id.take(8)} ${context.provenance()}"
            )
        }
        return buildString {
            appendLine("lakehouse_context_count=${contexts.count { it.hit }}")
            contexts.forEach { context ->
                appendLine(context.provenance())
                if (context.hit && context.content.isNotBlank()) {
                    appendLine(context.content)
                }
            }
        }.trimEnd()
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
