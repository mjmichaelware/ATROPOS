/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.planning

import atropos.core.dag.DagDefinition
import atropos.core.dag.DagNode
import java.util.ArrayDeque

data class PartitionCluster(
    val clusterId: String,
    val nodes: List<DagNode>,
    val tokenCount: Int,
    val assignedTerritory: Set<String>
) : List<String> by nodes.map({ it.id }) {
    val nodeIds: List<String> get() = nodes.map { it.id }
    val rootPaths: List<String> get() = assignedTerritory.toList()
    val estimatedTokens: Int get() = tokenCount
}

class PartitionClusterManager(
    private val maxTokenBudget: Int = 12000
) {
    companion object {
        const val MAX_PARALLEL_LANES: Int = 64

        /**
         * SD7-T2-012: Amdahl-derived effective parallelism formula subtracting coordination overhead:
         * S(N) = 1 / ((1 - p) + p/N + c * N)
         */
        fun effectiveParallelism(n: Int, parallelFraction: Double = 0.95, coordinationOverhead: Double = 0.002): Double {
            val clampedN = n.coerceIn(1, MAX_PARALLEL_LANES)
            val denominator = (1.0 - parallelFraction) + (parallelFraction / clampedN) + (coordinationOverhead * clampedN)
            return if (denominator > 0.0) 1.0 / denominator else 1.0
        }
    }

    private val cyclePartitioner: RelationalCycleUnificationPartitioner = RelationalCycleUnificationPartitioner()

    fun partition(dag: DagDefinition): List<PartitionCluster> {
        val readyNodes = dag.findReadyNodes()
        if (readyNodes.isEmpty()) return emptyList()

        // Critical-path scheduling prioritizing topological bottleneck atoms (nodes with most dependents)
        val orderedNodes = computeTopologicalOrder(
            readyNodes.sortedWith(
                compareByDescending<DagNode> { node ->
                    dag.nodes.count { it.dependencies.contains(node.id) }
                }.thenByDescending { estimateTokens(it) }
            ),
            dag
        )
        val clusters = mutableListOf<PartitionCluster>()
        var currentClusterNodes = mutableListOf<DagNode>()
        var currentTokens = 0
        var currentPaths = mutableSetOf<String>()

        for (node in orderedNodes) {
            val nodeTokens = estimateTokens(node)
            val nodePaths = node.territory.toSet()

            val exceedsBudget = (currentTokens + nodeTokens) > maxTokenBudget
            val hasConflict = hasPathOverlap(currentPaths.toList(), nodePaths.toList())

            if (currentClusterNodes.isNotEmpty() && (exceedsBudget || hasConflict)) {
                val candidateCluster = PartitionCluster(
                    clusterId = "cluster-${clusters.size}",
                    nodes = currentClusterNodes.toList(),
                    tokenCount = currentTokens,
                    assignedTerritory = currentPaths.toSet()
                )
                clusters.add(candidateCluster)
                currentClusterNodes = mutableListOf(node)
                currentTokens = nodeTokens
                currentPaths = nodePaths.toMutableSet()
            } else {
                currentClusterNodes.add(node)
                currentTokens += nodeTokens
                currentPaths.addAll(nodePaths)
            }
        }

        if (currentClusterNodes.isNotEmpty()) {
            clusters.add(
                PartitionCluster(
                    clusterId = "cluster-${clusters.size}",
                    nodes = currentClusterNodes.toList(),
                    tokenCount = currentTokens,
                    assignedTerritory = currentPaths.toSet()
                )
            )
        }

        val nodeDepMap = dag.nodes.associate { it.id to it.dependencies.toSet() }
        if (!verifyQuotientAcyclicity(clusters, nodeDepMap)) {
            return readyNodes.mapIndexed { idx, node ->
                PartitionCluster(
                    clusterId = "discrete-$idx",
                    nodes = listOf(node),
                    tokenCount = estimateTokens(node),
                    assignedTerritory = node.territory.toSet()
                )
            }
        }

        return clusters
    }

    fun unifyRelationalCycles(
        clusters: List<PartitionCluster>,
        relations: List<Pair<String, String>>
    ): List<PartitionCluster> = cyclePartitioner.unifyCycles(clusters, relations)

    private fun computeTopologicalOrder(nodes: List<DagNode>, dag: DagDefinition): List<DagNode> {
        val nodeMap = nodes.associateBy { it.id }
        val inDegree = mutableMapOf<String, Int>()
        val adj = mutableMapOf<String, MutableList<String>>()

        for (n in nodes) {
            inDegree[n.id] = 0
            adj[n.id] = mutableListOf()
        }

        for (n in nodes) {
            for (dep in n.dependencies) {
                if (dep in nodeMap) {
                    adj[dep]?.add(n.id)
                    inDegree[n.id] = (inDegree[n.id] ?: 0) + 1
                }
            }
        }

        val queue = ArrayDeque<String>()
        for ((id, deg) in inDegree) {
            if (deg == 0) queue.add(id)
        }

        val result = mutableListOf<DagNode>()
        while (queue.isNotEmpty()) {
            val curr = queue.removeFirst()
            nodeMap[curr]?.let { result.add(it) }
            for (neighbor in (adj[curr] ?: emptyList())) {
                val newDeg = (inDegree[neighbor] ?: 1) - 1
                inDegree[neighbor] = newDeg
                if (newDeg == 0) {
                    queue.add(neighbor)
                }
            }
        }

        if (result.size < nodes.size) {
            for (n in nodes) {
                if (n !in result) result.add(n)
            }
        }
        return result
    }

    fun verifyQuotientAcyclicity(clusters: List<PartitionCluster>, dependencies: Map<String, Set<String>>): Boolean {
        val clusterIds = clusters.map { it.clusterId }
        val clusterDeps = mutableMapOf<String, MutableSet<String>>()
        for (cluster in clusters) {
            clusterDeps[cluster.clusterId] = mutableSetOf()
        }

        for (cluster in clusters) {
            for (node in cluster.nodes) {
                val deps = dependencies[node.id] ?: emptySet()
                for (dep in deps) {
                    val depCluster = clusters.find { c -> c.nodes.any { it.id == dep } }
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
            for (neighbor in (clusterDeps[current] ?: emptySet())) {
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
                val pathsA = clusters[i].assignedTerritory
                val pathsB = clusters[j].assignedTerritory
                if (pathsA.any { a -> pathsB.any { b -> a.startsWith(b) || b.startsWith(a) } }) {
                    return false
                }
            }
        }
        return true
    }

    fun estimateTokens(node: DagNode): Int {
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