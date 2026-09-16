/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.agent

import atropos.core.dag.DagDefinition
import atropos.core.dag.DagNode
import atropos.core.planning.InternalBatchDefiner
import atropos.core.planning.InternalReadinessCalculator
import atropos.core.planning.InternalExecutionDagSynthesizer

/**
 * Batch planning for the self-host chain.
 *
 * The self-host run chain advances one node at a time via advanceNextResumableGoal.
 * This component adds batch planning breadth: it plans a batch of non-overlapping
 * ready nodes and returns them as a single execution unit.
 *
 * The batch planner uses the same territory-aware batching as DagExecutionService
 * but is decoupled for the self-host chain's single-advance semantics.
 */
class SelfHostBatchPlanner(
    private val batchDefiner: InternalBatchDefiner = InternalBatchDefiner(),
    private val readinessCalculator: InternalReadinessCalculator = InternalReadinessCalculator()
) {

    /**
     * Plans a batch of ready, non-overlapping nodes for the given goal.
     * Returns the planned batch as a list of node IDs, or empty if no ready nodes.
     */
    fun planBatch(goalId: String, dagService: DagExecutionService, goalId: String): List<String> {
        val dag = dagService.readDag(goalId) ?: return emptyList()
        val readyNodes = InternalReadinessCalculator().readyNodes(dag)
        
        if (readyNodes.isEmpty()) return emptyList()
        
        val batches = InternalBatchDefiner().define(dag)
        return batches.firstOrNull()?.map { it.id } ?: emptyList()
    }

    /**
     * Plans all batches for the given DAG.
     * Returns all batches as a list of lists of node IDs.
     */
    fun planAllBatches(dag: DagDefinition): List<List<String>> {
        val batches = InternalBatchDefiner().define(dag)
        return batches.map { it.map { it.id } }
    }

    /**
     * Checks if there are any ready nodes that can be batched.
     */
    fun hasReadyNodes(dag: DagDefinition): Boolean {
        return InternalReadinessCalculator().readyNodes(dag).isNotEmpty()
    }
}

/**
 * Batch execution result for the self-host chain.
 */
data class SelfHostBatchPlan(
    val batchId: String,
    val nodeIds: List<String>,
    val plannedAt: java.time.Instant = java.time.Instant.now()
) {
    fun isEmpty(): Boolean = nodeIds.isEmpty()
    fun size(): Int = nodeIds.size
}

/**
 * Batch planning result for the self-host chain.
 */
sealed class SelfHostBatchPlanningResult {
    data class Planned(val batch: SelfHostBatchPlan) : SelfHostBatchPlanningResult()
    data class NoReadyNodes(val message: String = "no ready nodes") : SelfHostBatchPlanningResult()
    data class DagNotFound(val dagId: String) : SelfHostBatchPlanningResult()
    data class Error(val message: String) : SelfHostBatchPlanningResult()
}