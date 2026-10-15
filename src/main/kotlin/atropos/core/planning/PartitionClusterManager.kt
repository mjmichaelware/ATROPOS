package atropos.core.planning

import atropos.core.dag.DagDefinition
import atropos.core.dag.DagNode
import atropos.core.dag.DagNodeState
import atropos.core.territory.TerritoryAssignment
import java.util.ArrayDeque

data class PartitionCluster(
    val clusterId: String,
    val nodeIds: List<String>,
    val rootPaths: List<String>,
    val estimatedTokens: Int
)

class PartitionClusterManager(
    private val maxTokenBudget: Int = 12000
) {
    fun partition(dag: DagDefinition): List<List<String>> {
        val readyNodes = dag.findReadyNodes()
        if (readyNodes.isEmpty()) return emptyList()

        val clusters = mutableListOf<PartitionCluster>()
        var currentCluster: MutableList<String> = mutableListOf()
        var currentTokens = 0
        var currentPaths = mutableListOf<String>()

        for (node in readyNodes) {
            val nodeTokens = estimateTokens(node)
            val nodePaths = node.territory

            if (currentTokens + nodeTokens > maxTokenBudget || hasPathOverlap(currentPaths, nodePaths)) {
                if (currentCluster.isNotEmpty()) {
                    clusters.add(PartitionCluster(
                        clusterId = "cluster-${clusters.size}",
                        nodeIds = currentCluster.toList(),
                        rootPaths = currentPaths.distinct(),
                        estimatedTokens = currentTokens
                    ))
                }
                currentCluster = mutableListOf(node.id)
                currentTokens = nodeTokens
                currentPaths = nodePaths.toMutableList()
            } else {
                currentCluster.add(node.id)
                currentTokens += nodeTokens
                currentPaths.addAll(nodePaths)
            }
        }

        if (currentCluster.isNotEmpty()) {
            clusters.add(PartitionCluster(
                clusterId = "cluster-${clusters.size}",
                nodeIds = currentCluster.toList(),
                rootPaths = currentPaths.distinct(),
                estimatedTokens = currentTokens
            ))
        }

        return clusters.map { it.nodeIds }
    }

    fun verifyQuotientAcyclicity(clusters: List<PartitionCluster>, dependencies: Map<String, Set<String>>): Boolean {
        val clusterIds = clusters.map { it.clusterId }
        val clusterDeps = mutableMapOf<String, MutableSet<String>>()
        for (cluster in clusters) {
            clusterDeps[cluster.clusterId] = mutableSetOf()
        }

        for (cluster in clusters) {
            for (nodeId in cluster.nodeIds) {
                val deps = dependencies[nodeId] ?: emptySet()
                for (dep in deps) {
                    val depCluster = clusters.find { it.nodeIds.contains(dep) }
                    if (depCluster != null && depCluster.clusterId != cluster.clusterId) {
                        clusterDeps[cluster.clusterId]!!.add(depCluster.clusterId)
                    }
                }
            }
        }

        val inDegree = mutableMapOf<String, Int>()
        for (cid in clusterIds) {
            inDegree[cid] = 0
        }
        for (deps in clusterDeps.values) {
            for (dep in deps) {
                inDegree[dep] = inDegree.getOrDefault(dep, 0) + 1
            }
        }

        val queue = ArrayDeque<String>()
        for ((cid, degree) in inDegree) {
            if (degree == 0) queue.add(cid)
        }

        var visited = 0
        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            visited++
            for (neighbor in clusterDeps[current]!!) {
                inDegree[neighbor] = inDegree[neighbor]!! - 1
                if (inDegree[neighbor]!! == 0) {
                    queue.add(neighbor)
                }
            }
        }

        return visited == clusterIds.size
    }

    fun checkPairwiseDisjoint(clusters: List<PartitionCluster>): Boolean {
        for (i in clusters.indices) {
            for (j in (i + 1) until clusters.size) {
                val pathsA = clusters[i].rootPaths.toSet()
                val pathsB = clusters[j].rootPaths.toSet()
                if (pathsA.any { a -> pathsB.any { b -> a.startsWith(b) || b.startsWith(a) } }) {
                    return false
                }
            }
        }
        return true
    }

    private fun estimateTokens(node: DagNode): Int {
        var tokens = 100
        if (node.actionPayload != null) {
            tokens += node.actionPayload!!.length / 4
        }
        tokens += node.territory.size * 50
        return tokens
    }

    private fun hasPathOverlap(current: List<String>, incoming: List<String>): Boolean {
        return current.any { c -> incoming.any { i -> c.startsWith(i) || i.startsWith(c) } }
    }
}