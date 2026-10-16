/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.planning

import java.util.Stack

class RelationalCycleUnificationPartitioner {
    fun unifyCycles(
        clusters: List<PartitionCluster>,
        relations: List<Pair<String, String>>
    ): List<PartitionCluster> {
        if (clusters.isEmpty() || relations.isEmpty()) return clusters

        val clusterById = clusters.associateBy { it.clusterId }
        val lookup = mutableMapOf<String, String>()

        for (c in clusters) {
            lookup[c.clusterId] = c.clusterId
            for (node in c.nodes) {
                lookup[node.id] = c.clusterId
            }
            for (path in c.assignedTerritory) {
                lookup[path] = c.clusterId
            }
        }

        fun resolveClusterId(key: String): String? {
            lookup[key]?.let { return it }
            for (c in clusters) {
                if (c.assignedTerritory.any { key.startsWith(it) || it.startsWith(key) }) {
                    return c.clusterId
                }
            }
            return null
        }

        val graph = clusters.associate { it.clusterId to mutableSetOf<String>() }
        for ((from, to) in relations) {
            val u = resolveClusterId(from)
            val v = resolveClusterId(to)
            if (u != null && v != null && u != v) {
                graph[u]?.add(v)
            }
        }

        var index = 0
        val indices = mutableMapOf<String, Int>()
        val lowLink = mutableMapOf<String, Int>()
        val onStack = mutableSetOf<String>()
        val stack = Stack<String>()
        val sccs = mutableListOf<List<String>>()

        fun strongConnect(v: String) {
            indices[v] = index
            lowLink[v] = index
            index++
            stack.push(v)
            onStack.add(v)

            for (w in (graph[v] ?: emptySet())) {
                if (!indices.containsKey(w)) {
                    strongConnect(w)
                    lowLink[v] = minOf(lowLink[v]!!, lowLink[w]!!)
                } else if (onStack.contains(w)) {
                    lowLink[v] = minOf(lowLink[v]!!, indices[w]!!)
                }
            }

            if (lowLink[v] == indices[v]) {
                val scc = mutableListOf<String>()
                while (true) {
                    val w = stack.pop()
                    onStack.remove(w)
                    scc.add(w)
                    if (w == v) break
                }
                sccs.add(scc)
            }
        }

        for (c in clusters) {
            if (!indices.containsKey(c.clusterId)) {
                strongConnect(c.clusterId)
            }
        }

        val unified = mutableListOf<PartitionCluster>()
        val handled = mutableSetOf<String>()

        for (scc in sccs) {
            if (scc.size > 1) {
                val memberClusters = scc.mapNotNull { clusterById[it] }
                val mergedId = scc.sorted().joinToString("+")
                val mergedNodes = memberClusters.flatMap { it.nodes }.distinctBy { it.id }
                val mergedTokens = memberClusters.sumOf { it.tokenCount }
                val mergedTerritory = memberClusters.flatMap { it.assignedTerritory }.toSet()

                unified.add(
                    PartitionCluster(
                        clusterId = mergedId,
                        nodes = mergedNodes,
                        tokenCount = mergedTokens,
                        assignedTerritory = mergedTerritory
                    )
                )
                handled.addAll(scc)
            }
        }

        for (c in clusters) {
            if (c.clusterId !in handled) {
                unified.add(c)
            }
        }

        return unified
    }
}
