/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.specgraph

import atropos.core.dag.DagDefinition
import atropos.core.dag.DagNode
import atropos.core.dag.DagNodeAction
import atropos.core.dag.DagNodeState
import java.nio.file.Path
import java.time.Instant

/**
 * Turns a verified SpecGraph handoff into an ATROPOS [DagDefinition].
 *
 * The counterpart of [atropos.core.planning.InternalExecutionDagSynthesizer],
 * and the point of the whole ingest: that synthesizer builds a DAG by
 * re-deriving structure from atoms, because the only SpecGraph stage ATROPOS
 * consumed was atom extraction. SpecGraph had already synthesized *and verified*
 * an execution graph — three staged nodes per atom, edges between them, a ready
 * set — and ATROPOS threw it away with the database it lived in.
 *
 * This translates rather than re-plans. Node identity, ordering and staging come
 * from the handoff unchanged; what is added is only what ATROPOS's executor
 * needs and SpecGraph has no opinion about — an action kind and a payload.
 *
 * Nothing here invents a node. A handoff node with no ATROPOS action is refused,
 * not defaulted, because a node silently mapped to the wrong action is a step
 * that runs and reports success having done something other than what the plan
 * required.
 */
class HandoffDagTranslator {

    /**
     * Translates [handoff] into a definition rooted at [repoRoot].
     *
     * @return a [Translation] carrying the definition and what was refused. An
     *   unverified or cyclic handoff yields no definition at all — the checks in
     *   [HandoffDocument.executability] are preconditions, not warnings.
     */
    fun translate(
        handoff: HandoffDocument,
        repoRoot: Path,
        now: Instant = Instant.now()
    ): Translation {
        val executability = handoff.executability()
        if (!executability.executable) {
            return Translation.refused(executability.blockers())
        }

        val graph = handoff.execution
        val statementByAtom = handoff.requirements.associate { it.atomId to it.statement }
        val sourceByAtom = handoff.requirements.associate { it.atomId to it.source }

        val refusals = mutableListOf<String>()
        val nodes = graph.nodes.mapNotNull { node ->
            val action = actionFor(node.nodeType)
            if (action == null) {
                refusals += "unmapped_node_type:${node.nodeType.ifBlank { "blank" }} node=${node.id}"
                return@mapNotNull null
            }

            DagNode(
                id = node.id,
                label = node.title.ifBlank { node.nodeKey },
                dependencies = graph.dependenciesOf(node.id),
                territory = emptyList(),
                action = action,
                actionPayload = payloadFor(node, statementByAtom, sourceByAtom, handoff),
                state = initialStateFor(node),
                createdAt = now,
                updatedAt = now,
                metaFile = repoRoot.resolve(
                    ".atropos/dag/execution/definitions/specgraph-${node.id}.meta"
                )
            )
        }

        // Dependencies are filtered against the surviving node set. A refused
        // node leaves dangling edges, and a dependency on a node that will never
        // exist blocks its dependant forever with nothing in the log to explain
        // the stall.
        val surviving = nodes.map { it.id }.toSet()
        val cleaned = nodes.map { node ->
            val kept = node.dependencies.filter { it in surviving }
            if (kept.size == node.dependencies.size) node
            else {
                refusals += "dropped_dependency node=${node.id} " +
                    "missing=${(node.dependencies - surviving).joinToString(",")}"
                node.copy(dependencies = kept)
            }
        }

        return Translation(
            definition = DagDefinition(
                id = "specgraph-${handoff.plan.executionGraphId.ifBlank { handoff.plan.id }}",
                label = "SpecGraph plan ${handoff.plan.id} (${handoff.project.slug})",
                projectId = handoff.project.id,
                nodes = cleaned,
                createdAt = now,
                updatedAt = now,
                metaFile = repoRoot.resolve(".atropos/dag/execution/definitions/specgraph.meta")
            ),
            refusals = refusals.toList(),
            blockers = emptyList(),
            handoff = handoff
        )
    }

    /**
     * The ATROPOS action a SpecGraph stage implies.
     *
     * SpecGraph's three stages are a contract, its implementation, and its
     * verification. The mapping is deliberately narrow:
     *
     * - `CONTRACT` states what must be true. It produces a specification, not a
     *   file, so it is a provider call whose payload is the requirement.
     * - `IMPLEMENTATION` is likewise a provider call — generation, not mutation.
     *   Nothing here writes to disk, for the reason the internal synthesizer
     *   already states: generation and mutation must stay on opposite sides of
     *   the gate rather than fusing into one unreviewable step.
     * - `VERIFICATION` checks the work and maps to [DagNodeAction.VERIFY].
     *
     * An unrecognised stage returns null rather than a default. SpecGraph could
     * add a fourth stage, and mapping it to whatever the current `else` branch
     * happens to be would run it as the wrong kind of step while reporting
     * success.
     */
    private fun actionFor(nodeType: String): DagNodeAction? =
        when (nodeType.trim().uppercase()) {
            "CONTRACT" -> DagNodeAction.PROVIDER_CALL
            "IMPLEMENTATION" -> DagNodeAction.PROVIDER_CALL
            "VERIFICATION" -> DagNodeAction.VERIFY
            else -> null
        }

    /**
     * A node's initial state.
     *
     * SpecGraph marks a node `BLOCKED` when its atom still has open research
     * dimensions. That judgement is carried across rather than recomputed: it
     * was made with the research results, which ATROPOS does not have, and
     * starting such a node would build against a requirement SpecGraph itself
     * said was not settled.
     */
    private fun initialStateFor(node: HandoffNode): DagNodeState =
        if (node.blocked) DagNodeState.BLOCKED else DagNodeState.PENDING

    /**
     * What the node's executor is given.
     *
     * The requirement statement and its source coordinate, so a node carries the
     * sentence that required it. This is the traceability artifact doing real
     * work rather than sitting in a file: without it the executor receives a
     * title like "Implement: ..." truncated by `_sanitize_export_title` and has
     * no access to the full statement at all.
     */
    private fun payloadFor(
        node: HandoffNode,
        statementByAtom: Map<String, String>,
        sourceByAtom: Map<String, String>,
        handoff: HandoffDocument
    ): String = buildString {
        appendLine("stage=${node.nodeType}")
        appendLine("specgraph_node_key=${node.nodeKey}")
        node.atomId?.let { atom ->
            appendLine("atom_id=$atom")
            sourceByAtom[atom]?.takeIf { it.isNotBlank() }?.let { appendLine("source=$it") }
            statementByAtom[atom]?.takeIf { it.isNotBlank() }?.let {
                appendLine("requirement=$it")
            }
        }
        if (node.openDimensions > 0) {
            appendLine("open_research_dimensions=${node.openDimensions}")
        }
        appendLine("plan_id=${handoff.plan.id}")
        appendLine("plan_input_fingerprint=${handoff.plan.inputFingerprint}")
    }.trimEnd()
}

/**
 * The outcome of translating a handoff.
 *
 * [blockers] and [refusals] are different failures and are kept apart.
 * A blocker means the handoff must not be executed at all; a refusal means one
 * node could not be represented and the rest still can. Collapsing them would
 * either discard a usable plan or execute a forbidden one.
 */
data class Translation(
    val definition: DagDefinition?,
    val refusals: List<String>,
    val blockers: List<String>,
    val handoff: HandoffDocument?
) {
    val usable: Boolean
        get() = definition != null && blockers.isEmpty()

    /** Requirements the plan does not implement. Empty on a well-formed plan. */
    fun orphanedRequirements(): List<HandoffRequirement> =
        handoff?.requirements?.filter { it.mandatory && it.orphaned }.orEmpty()

    /** Mandatory requirements with no VERIFICATION node. */
    fun unverifiedRequirements(): List<HandoffRequirement> =
        handoff?.requirements
            ?.filter { it.mandatory && !it.orphaned && "VERIFICATION" !in it.coveredStages }
            .orEmpty()

    fun evidenceLine(): String = when {
        definition == null ->
            "SKIPPED_SOFT_FAIL:specgraph_handoff_not_executable ${blockers.joinToString(",")}"
        refusals.isNotEmpty() ->
            "PASS:specgraph_handoff_translated nodes=${definition.nodes.size} " +
                "refused=${refusals.size} first_refusal=${refusals.first()}"
        else ->
            "PASS:specgraph_handoff_translated nodes=${definition.nodes.size} " +
                "plan=${handoff?.plan?.id.orEmpty()} " +
                "orphaned=${orphanedRequirements().size} " +
                "unverified=${unverifiedRequirements().size}"
    }

    companion object {
        fun refused(blockers: List<String>): Translation =
            Translation(definition = null, refusals = emptyList(), blockers = blockers, handoff = null)
    }
}
