package atropos.core.planning

import atropos.core.dag.DagDefinition
import atropos.core.dag.DagNode
import atropos.core.dag.DagNodeState

class InternalReadinessCalculator {
    fun readyNodes(dag: DagDefinition): List<DagNode> = dag.findReadyNodes()

    fun pendingNodes(dag: DagDefinition): List<DagNode> =
        dag.nodes.filter { it.state == DagNodeState.PENDING }

    fun runningNodes(dag: DagDefinition): List<DagNode> =
        dag.nodes.filter { it.state in setOf(DagNodeState.CLAIMED, DagNodeState.RUNNING, DagNodeState.VERIFYING) }

    fun completedNodes(dag: DagDefinition): List<DagNode> =
        dag.nodes.filter { it.state == DagNodeState.COMPLETE }

    fun rejectCycles(nodes: List<DagNode>) {
        val remaining = nodes.associate { it.id to it.dependencies.size }.toMutableMap()
        val outgoing = mutableMapOf<String, MutableList<String>>()
        nodes.forEach { node ->
            node.dependencies.forEach { dependency ->
                outgoing.getOrPut(dependency) { mutableListOf() }.add(node.id)
            }
        }
        val queue = ArrayDeque(nodes.filter { it.dependencies.isEmpty() }.map { it.id })
        var visited = 0
        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            visited++
            outgoing[current].orEmpty().forEach { dependent ->
                val next = (remaining[dependent] ?: 0) - 1
                remaining[dependent] = next
                if (next == 0) queue.addLast(dependent)
            }
        }
        require(visited == nodes.size) {
            val cycleNodes = remaining.filterValues { it > 0 }.keys.sorted()
            "execution DAG must be acyclic; cycle detected involving: ${cycleNodes.joinToString(", ")}"
        }
    }
}
