package atropos.core.planning

import atropos.core.dag.DagDefinition
import atropos.core.dag.DagNode

class InternalBatchDefiner(
    private val readinessCalculator: InternalReadinessCalculator = InternalReadinessCalculator()
) {
    fun define(dag: DagDefinition): List<List<DagNode>> {
        val ready = readinessCalculator.readyNodes(dag)
        val batches = mutableListOf<List<DagNode>>()
        val assigned = mutableSetOf<String>()
        for (node in ready) {
            if (node.id in assigned) continue
            val batch = mutableListOf(node)
            assigned += node.id
            for (candidate in ready) {
                if (candidate.id in assigned) continue
                if (!overlaps(node.territory, candidate.territory)) {
                    batch += candidate
                    assigned += candidate.id
                }
            }
            batches += batch
        }
        return batches
    }

    private fun overlaps(left: List<String>, right: List<String>): Boolean =
        left.any { territory -> right.any { it == territory || it.startsWith(territory) || territory.startsWith(it) } }
}
