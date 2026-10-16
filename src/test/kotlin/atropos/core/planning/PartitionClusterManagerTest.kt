/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.planning

import atropos.core.dag.DagDefinition
import atropos.core.dag.DagNode
import atropos.core.dag.DagNodeState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PartitionClusterManagerTest {

    @Test
    fun `partitions independent nodes within token budget`() {
        val manager = PartitionClusterManager(maxTokenBudget = 1000)
        val node1 = DagNode(id = "n1", label = "Node 1", territory = listOf("src/a"), state = DagNodeState.READY)
        val node2 = DagNode(id = "n2", label = "Node 2", territory = listOf("src/b"), state = DagNodeState.READY)
        val dag = DagDefinition(label = "dag1", nodes = listOf(node1, node2), projectId = "p1")

        val clusters = manager.partition(dag)
        assertEquals(1, clusters.size)
        assertEquals(setOf("n1", "n2"), clusters[0].nodes.map { it.id }.toSet())
    }

    @Test
    fun `unifies cyclic relational modules via Tarjan SCC`() {
        val partitioner = RelationalCycleUnificationPartitioner()
        val nodeA = DagNode(id = "mA", label = "Module A", territory = listOf("pkg/a"), state = DagNodeState.READY)
        val nodeB = DagNode(id = "mB", label = "Module B", territory = listOf("pkg/b"), state = DagNodeState.READY)
        val nodeC = DagNode(id = "mC", label = "Module C", territory = listOf("pkg/c"), state = DagNodeState.READY)

        val clusterA = PartitionCluster(clusterId = "cA", nodes = listOf(nodeA), tokenCount = 100, assignedTerritory = setOf("pkg/a"))
        val clusterB = PartitionCluster(clusterId = "cB", nodes = listOf(nodeB), tokenCount = 100, assignedTerritory = setOf("pkg/b"))
        val clusterC = PartitionCluster(clusterId = "cC", nodes = listOf(nodeC), tokenCount = 100, assignedTerritory = setOf("pkg/c"))

        // Cycle between cA and cB; cC is independent
        val relations = listOf(
            "cA" to "cB",
            "cB" to "cA",
            "cB" to "cC"
        )

        val unified = partitioner.unifyCycles(listOf(clusterA, clusterB, clusterC), relations)
        assertEquals(2, unified.size)

        val merged = unified.first { it.nodes.size == 2 }
        assertEquals(setOf("mA", "mB"), merged.nodes.map { it.id }.toSet())
        assertEquals(setOf("pkg/a", "pkg/b"), merged.assignedTerritory)
    }
}
