/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.specgraph

data class HandoffProject(val id: String, val slug: String, val name: String)

data class HandoffPlan(
    val id: String,
    val status: String,
    val inputFingerprint: String,
    val authorityGraphId: String,
    val executionGraphId: String
) {
    val verified: Boolean
        get() = status.trim().uppercase() in VERIFIED_STATUSES

    private companion object {
        val VERIFIED_STATUSES = setOf("VERIFIED", "READY", "APPROVED")
    }
}

data class HandoffNode(
    val id: String,
    val nodeKey: String,
    val nodeType: String,
    val title: String,
    val status: String,
    val atomId: String? = null,
    val openDimensions: Int = 0
) {
    val blocked: Boolean
        get() = status.trim().uppercase() == "BLOCKED"
}

data class HandoffEdge(val fromNodeId: String, val toNodeId: String, val edgeType: String)

data class HandoffExecutionGraph(
    val graphId: String,
    val nodes: List<HandoffNode>,
    val edges: List<HandoffEdge>,
    val readyNodeIds: List<String>
) {
    fun dependenciesOf(nodeId: String): List<String> =
        edges.filter { it.toNodeId == nodeId }.map { it.fromNodeId }

    fun acyclic(): Boolean {
        val outgoing = edges.groupBy({ it.fromNodeId }, { it.toNodeId })
        val visiting = mutableSetOf<String>()
        val settled = mutableSetOf<String>()

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

    fun roots(): List<HandoffNode> {
        val hasIncoming = edges.map { it.toNodeId }.toSet()
        return nodes.filter { it.id !in hasIncoming }
    }
}

data class HandoffRequirement(
    val atomId: String,
    val statement: String,
    val kind: String,
    val modality: String,
    val source: String,
    val planNodes: List<HandoffPlanBinding>
) {
    val planNodeIds: List<String>
        get() = planNodes.sortedBy { it.sequenceNumber }.map { it.graphNodeId }

    val coveredStages: Set<String>
        get() = planNodes.map { it.stage.trim().uppercase() }.filter { it.isNotEmpty() }.toSet()

    val fullyStaged: Boolean
        get() = coveredStages.containsAll(setOf("CONTRACT", "IMPLEMENTATION", "VERIFICATION"))

    val mandatory: Boolean
        get() = modality.trim().uppercase() !in setOf("MAY", "OPTIONAL", "SHOULD")

    val orphaned: Boolean
        get() = planNodes.isEmpty()
}

data class HandoffPlanBinding(
    val graphNodeId: String,
    val atomId: String,
    val stage: String,
    val sequenceNumber: Int
)

data class HandoffExecutionContract(
    val authorityOwner: String,
    val runtimeOwner: String,
    val sourceAuthorityIsImmutable: Boolean,
    val executionGraphMustBeAcyclic: Boolean,
    val implementationRequiresVerification: Boolean
)

data class Executability(
    val planVerified: Boolean,
    val acyclic: Boolean,
    val hasReadyWork: Boolean,
    val contractHonoured: Boolean
) {
    val executable: Boolean
        get() = planVerified && acyclic && hasReadyWork && contractHonoured

    fun blockers(): List<String> = buildList {
        if (!planVerified) add("plan_not_verified")
        if (!acyclic) add("execution_graph_cyclic")
        if (!hasReadyWork) add("no_ready_nodes")
        if (!contractHonoured) add("runtime_owner_not_atropos")
    }
}
