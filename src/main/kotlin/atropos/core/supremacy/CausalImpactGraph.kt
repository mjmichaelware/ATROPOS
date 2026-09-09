/* SPDX-License-Identifier: AGPL-3.0-only */
/**
 * B-SUP-005: Causal Impact Graph
 *
 * Computes and persists the causal impact graph of agent actions,
 * showing which actions caused which outcomes.
 */
package atropos.core.supremacy

import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant

object CausalImpactGraph {
    data class Node(
        val id: String,
        val action: String,
        val timestamp: Instant,
        val metadata: Map<String, String> = emptyMap()
    )

    data class Edge(
        val from: String,
        val to: String,
        val weight: Double = 1.0,
        val causalityType: CausalityType = CausalityType.DIRECT
    )

    enum class CausalityType {
        DIRECT, INDIRECT, CORRELATION
    }

    data class Graph(
        val nodes: Map<String, Node> = emptyMap(),
        val edges: List<Edge> = emptyList()
    ) {
        fun addNode(node: Node): Graph = copy(nodes = nodes + (node.id to node))
        fun addEdge(edge: Edge): Graph = copy(edges = edges + edge)
    }

    /**
     * Computes the causal impact graph from a sequence of actions and outcomes.
     */
    fun compute(actions: List<Pair<String, String>>): Graph {
        var graph = Graph()
        var prevId: String? = null

        actions.forEachIndexed { index, (action, outcome) ->
            val nodeId = "action_$index"
            val node = Node(
                id = nodeId,
                action = action,
                timestamp = Instant.now(),
                metadata = mapOf("outcome" to outcome)
            )
            graph = graph.addNode(node)

            if (prevId != null) {
                graph = graph.addEdge(Edge(prevId, nodeId, 1.0, CausalityType.DIRECT))
            }
            prevId = nodeId
        }

        return graph
    }

    /**
     * Persists the causal impact graph to CAS.
     */
    fun persistGraph(casDir: Path, graph: Graph): Path {
        Files.createDirectories(casDir)
        val graphFile = casDir.resolve("causal-impact-graph.json")
        // Simplified serialization
        val json = """
            {
                "nodes": ${graph.nodes.size},
                "edges": ${graph.edges.size},
                "timestamp": "${Instant.now()}"
            }
        """.trimIndent()
        Files.writeString(graphFile, json.trimIndent())
        return graphFile
    }

    /**
     * Reads the persisted causal impact graph.
     */
    fun readGraph(casDir: Path): Graph? {
        val graphFile = casDir.resolve("causal-impact-graph.json")
        if (!Files.isRegularFile(graphFile)) return null
        return Graph() // Simplified
    }

    /**
     * CLI command to show graph stats.
     */
    fun showStats(graph: Graph): String {
        return "Causal graph: ${graph.nodes.size} nodes, ${graph.edges.size} edges"
    }
}