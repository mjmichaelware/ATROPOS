/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.specgraph

/**
 * A parsed `atropos_handoff.json`, schema `specgraph.atropos.handoff.v1`.
 *
 * The document SpecGraph writes *to* ATROPOS. Its `execution_contract` names
 * `specgraph-foundry` as the authority owner and `atropos` as the runtime
 * owner, which is the division this type exists to honour: the plan arrives
 * decided, and ATROPOS's job is to run it, not to re-derive it.
 *
 * Everything here is read-only. The handoff is an immutable statement about a
 * verified plan; a mutable model of it invites a caller to "fix up" a node and
 * execute something the authority side never approved.
 */
data class HandoffDocument(
    val schema: String,
    val producer: String,
    val project: HandoffProject,
    val plan: HandoffPlan,
    val execution: HandoffExecutionGraph,
    val requirements: List<HandoffRequirement>,
    val routingLaw: List<String>,
    val contract: HandoffExecutionContract
) {
    /**
     * Whether this handoff may be executed at all.
     *
     * Three independent conditions, all required. A plan that is not verified
     * has not passed `verify_plan`; a cyclic graph cannot be ordered; and a
     * graph with no ready node cannot be started. Each of the three produces a
     * different wrong behaviour if ignored, so they are reported separately
     * rather than collapsed into one boolean.
     */
    fun executability(): Executability = Executability(
        planVerified = plan.verified,
        acyclic = execution.acyclic(),
        hasReadyWork = execution.readyNodeIds.isNotEmpty(),
        contractHonoured = contract.runtimeOwner == "atropos"
    )

    companion object {
        const val SCHEMA = "specgraph.atropos.handoff.v1"
    }
}

/** The project the plan belongs to. */
data class HandoffProject(val id: String, val slug: String, val name: String)

/**
 * The plan's identity and verification status.
 *
 * [inputFingerprint] is the reason a handoff can be trusted to be about a
 * particular set of sources: SpecGraph computes it over the plan's inputs, so
 * two handoffs with the same fingerprint describe the same work and one with a
 * different fingerprint describes different work regardless of how similar it
 * reads.
 */
data class HandoffPlan(
    val id: String,
    val status: String,
    val inputFingerprint: String,
    val authorityGraphId: String,
    val executionGraphId: String
) {
    /**
     * True when `verify_plan` passed.
     *
     * Compared case-insensitively against the statuses SpecGraph writes.
     * Anything else — `DRAFT`, `FAILED`, a status added later — is not
     * verified. Defaulting an unrecognised status to "verified" is how an
     * unverified plan gets executed, so the unknown case falls the safe way.
     */
    val verified: Boolean
        get() = status.trim().uppercase() in VERIFIED_STATUSES

    private companion object {
        val VERIFIED_STATUSES = setOf("VERIFIED", "READY", "APPROVED")
    }
}

/** One node of the synthesized execution DAG. */
data class HandoffNode(
    val id: String,
    val nodeKey: String,
    /** `CONTRACT`, `IMPLEMENTATION` or `VERIFICATION` — SpecGraph's `STAGES`. */
    val nodeType: String,
    val title: String,
    val status: String,
    /**
     * The atom this node implements, from the node's `payload_json`.
     *
     * Null when the payload is absent or unreadable, which is the honest answer
     * — a node whose atom cannot be identified must not be silently attributed
     * to a neighbouring one.
     */
    val atomId: String? = null,
    /** Unresolved research dimensions on this node's atom at synthesis time. */
    val openDimensions: Int = 0
) {
    /** True when SpecGraph blocked this node on unresolved research. */
    val blocked: Boolean
        get() = status.trim().uppercase() == "BLOCKED"
}

/** One edge, in SpecGraph's `from -> to` direction. */
data class HandoffEdge(val fromNodeId: String, val toNodeId: String, val edgeType: String)

/**
 * The DAG as SpecGraph synthesized it.
 *
 * SpecGraph enforces acyclicity when building the graph, so [acyclic] is a
 * re-check rather than a discovery. It is here because the guarantee was
 * established in another process at another time, and a consumer that executes
 * a cyclic graph deadlocks in a way that is very hard to read backwards.
 */
data class HandoffExecutionGraph(
    val graphId: String,
    val nodes: List<HandoffNode>,
    val edges: List<HandoffEdge>,
    val readyNodeIds: List<String>
) {
    /** Dependencies per node id: the nodes that must complete before it. */
    fun dependenciesOf(nodeId: String): List<String> =
        edges.filter { it.toNodeId == nodeId }.map { it.fromNodeId }

    /** True when the edges contain no cycle. */
    fun acyclic(): Boolean {
        val outgoing = edges.groupBy({ it.fromNodeId }, { it.toNodeId })
        val visiting = mutableSetOf<String>()
        val settled = mutableSetOf<String>()

        // Iterative rather than recursive: a plan for a large document runs to
        // three nodes per atom, and a recursive walk over thousands of them is
        // the StackOverflowError this codebase has already paid for once.
        fun hasCycleFrom(start: String): Boolean {
            val stack = ArrayDeque<Pair<String, Boolean>>()
            stack.addLast(start to false)
            while (stack.isNotEmpty()) {
                val (node, exiting) = stack.removeLast()
                if (exiting) {
                    visiting -= node
                    settled += node
                    continue
                }
                if (node in settled) continue
                if (node in visiting) return true
                visiting += node
                stack.addLast(node to true)
                outgoing[node].orEmpty().forEach { stack.addLast(it to false) }
            }
            return false
        }

        return nodes.none { it.id !in settled && hasCycleFrom(it.id) }
    }

    /** Nodes with no incoming edge — where execution can begin. */
    fun roots(): List<HandoffNode> {
        val hasIncoming = edges.map { it.toNodeId }.toSet()
        return nodes.filter { it.id !in hasIncoming }
    }
}

/**
 * One requirement, traced from its atom to the plan nodes that satisfy it.
 *
 * This is the artifact that makes a completed run auditable against the source
 * document rather than against the plan alone: every node points back at the
 * sentence that required it.
 */
data class HandoffRequirement(
    val atomId: String,
    val statement: String,
    val kind: String,
    val modality: String,
    val source: String,
    /**
     * The plan-node bindings implementing this requirement.
     *
     * Objects rather than bare ids, because that is what `plan_node_bindings`
     * rows are: each carries the node, the stage it covers and its sequence.
     * Reducing them to ids at parse time would discard the stage, and "which
     * requirements have an implementation node but no verification node" is
     * exactly the question a traceability artifact exists to answer.
     */
    val planNodes: List<HandoffPlanBinding>
) {
    /** The bound graph node ids, in sequence order. */
    val planNodeIds: List<String>
        get() = planNodes.sortedBy { it.sequenceNumber }.map { it.graphNodeId }

    /** The stages this requirement has a node for. */
    val coveredStages: Set<String>
        get() = planNodes.map { it.stage.trim().uppercase() }.filter { it.isNotEmpty() }.toSet()

    /**
     * True when the plan covers this requirement end to end.
     *
     * SpecGraph synthesizes three nodes per atom. A requirement missing its
     * VERIFICATION node would be built and never checked, which is the failure
     * `implementation_requires_verification` in the execution contract forbids.
     */
    val fullyStaged: Boolean
        get() = coveredStages.containsAll(setOf("CONTRACT", "IMPLEMENTATION", "VERIFICATION"))

    /**
     * True when this requirement is mandatory.
     *
     * SpecGraph's modality distinguishes a requirement from a recommendation.
     * An unrecognised modality counts as mandatory — under-building against a
     * spec is the more expensive error, and treating an unknown modality as
     * optional silently drops requirements.
     */
    val mandatory: Boolean
        get() = modality.trim().uppercase() !in setOf("MAY", "OPTIONAL", "SHOULD")

    /** True when nothing in the plan implements this requirement. */
    val orphaned: Boolean
        get() = planNodes.isEmpty()
}

/**
 * One `plan_node_bindings` row: the link from an atom to the node covering it.
 *
 * [sequenceNumber] is the plan's own ordering, preserved so a rendered
 * requirement lists its nodes in the order SpecGraph laid them out rather than
 * in JSON order, which is only incidentally the same.
 */
data class HandoffPlanBinding(
    val graphNodeId: String,
    val atomId: String,
    val stage: String,
    val sequenceNumber: Int
)

/** The ownership terms the handoff states. */
data class HandoffExecutionContract(
    val authorityOwner: String,
    val runtimeOwner: String,
    val sourceAuthorityIsImmutable: Boolean,
    val executionGraphMustBeAcyclic: Boolean,
    val implementationRequiresVerification: Boolean
)

/**
 * Why a handoff may or may not be executed.
 *
 * Separate booleans rather than one verdict, so a refusal can say which
 * condition failed. "This handoff cannot be executed" sends an operator to read
 * two codebases; "plan status is DRAFT, not verified" does not.
 */
data class Executability(
    val planVerified: Boolean,
    val acyclic: Boolean,
    val hasReadyWork: Boolean,
    val contractHonoured: Boolean
) {
    val executable: Boolean
        get() = planVerified && acyclic && hasReadyWork && contractHonoured

    /** The failed conditions, named. Empty when [executable]. */
    fun blockers(): List<String> = buildList {
        if (!planVerified) add("plan_not_verified")
        if (!acyclic) add("execution_graph_cyclic")
        if (!hasReadyWork) add("no_ready_nodes")
        if (!contractHonoured) add("runtime_owner_not_atropos")
    }
}
