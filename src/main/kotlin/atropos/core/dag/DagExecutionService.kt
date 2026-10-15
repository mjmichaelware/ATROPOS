package atropos.core.dag

import atropos.core.AtroposConfig
import atropos.core.AtroposRepoRootLocator
import atropos.core.planning.NodeResult
import atropos.core.planning.PartitionClusterManager
import atropos.core.territory.TerritoryGrantService
import atropos.core.territory.GrantResult
import atropos.core.territory.TerritoryService
import atropos.core.territory.TerritoryStore
import atropos.core.verification.VerifiedCompletionGate
import atropos.core.worktree.IsolatedWorktreeService
import atropos.core.worktree.WorktreeCreateResult
import atropos.core.worktree.WorktreeRollbackResult
import atropos.core.policy.ActionActor
import java.nio.file.Path
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

class DagExecutionService(
    private val config: AtroposConfig = AtroposConfig.load(),
    private val repoRoot: Path = AtroposRepoRootLocator.resolve(),
    private val worktreeService: IsolatedWorktreeService = IsolatedWorktreeService(repoRoot),
    private val territoryService: TerritoryGrantService = TerritoryGrantService(TerritoryService(TerritoryStore(repoRoot))),
    private val partitionManager: PartitionClusterManager = PartitionClusterManager(),
    private val completionGate: VerifiedCompletionGate = VerifiedCompletionGate()
) {
    private val activeWorktrees = ConcurrentHashMap<String, WorktreeCreateResult>()

    fun executeWaveBatch(dagId: String, waveNodes: List<DagNode>): Map<String, DagNodeExecutionResult> {
        val results = mutableMapOf<String, DagNodeExecutionResult>()

        val disjoint = enforceTerritoryDisjointness(waveNodes)
        if (!disjoint) {
            for (node in waveNodes) {
                results[node.id] = DagNodeExecutionResult(
                    nodeId = node.id,
                    state = DagNodeState.BLOCKED,
                    ok = false,
                    message = "territory overlap detected in wave"
                )
            }
            return results
        }

        for (node in waveNodes) {
            val result = executeNodeInWorktree(node, dagId)
            results[node.id] = result
        }

        return results
    }

    fun evaluateDagBatched(dagId: String): DagExecutionResult {
        val dag = DagStore(repoRoot).readDag(dagId)
            ?: return DagExecutionResult(dagId, false, 0, 0, 0, "DAG not found: $dagId", emptyList())

        val allResults = mutableListOf<DagNodeExecutionResult>()
        var currentDag = dag

        while (true) {
            val readyNodes = currentDag.findReadyNodes()
            if (readyNodes.isEmpty()) break

            val waveClusters = partitionManager.partition(currentDag)
            if (waveClusters.isEmpty()) break

            for (cluster in waveClusters) {
                val waveNodes = cluster.mapNotNull { id -> currentDag.findNode(id) }
                if (waveNodes.isEmpty()) continue

                val waveResults = executeWaveBatch(dagId, waveNodes)
                allResults.addAll(waveResults.values)

                currentDag = DagStore(repoRoot).readDag(dagId)
                    ?: return DagExecutionResult(dagId, false, 0, 0, 0, "DAG vanished during evaluation", allResults)
            }
        }

        val completed = allResults.count { it.state == DagNodeState.COMPLETE }
        val failed = allResults.count { it.state == DagNodeState.FAILED }
        val blocked = allResults.count { it.state == DagNodeState.BLOCKED }
        val totalNodes = dag.nodes.size

        return DagExecutionResult(
            dagId = dagId,
            ok = failed == 0 && blocked == 0 && completed == totalNodes,
            completedNodes = completed,
            failedNodes = failed,
            blockedNodes = blocked,
            message = "DAG batched evaluation: $completed completed, $failed failed, $blocked blocked out of $totalNodes",
            nodeResults = allResults
        )
    }

    private fun enforceTerritoryDisjointness(nodes: List<DagNode>): Boolean {
        if (nodes.size <= 1) return true

        val territories = nodes.map { it.territory.toSet() }
        for (i in territories.indices) {
            for (j in (i + 1) until territories.size) {
                if (territories[i].intersect(territories[j]).isNotEmpty()) {
                    return false
                }
            }
        }
        return true
    }

    private fun executeNodeInWorktree(node: DagNode, dagId: String): DagNodeExecutionResult {
        if (node.state != DagNodeState.READY && node.state != DagNodeState.PENDING) {
            return DagNodeExecutionResult(node.id, node.state, false, "node already in state ${node.state}")
        }

        val worktreeResult = worktreeService.createWorktree(jobId = node.id, territory = node.territory)
        if (!worktreeResult.success) {
            return DagNodeExecutionResult(node.id, DagNodeState.FAILED, false, worktreeResult.message)
        }
        if (worktreeResult.record == null) {
            return DagNodeExecutionResult(node.id, DagNodeState.FAILED, false, "worktree created but record missing")
        }

        val worktreeRecord = worktreeResult.record!!
        activeWorktrees[node.id] = worktreeResult

        try {
            executeNodeActions(node, worktreeRecord.worktreePath)

            val gateReport = completionGate.evaluateNode(node)
            if (!gateReport.canComplete) {
                worktreeService.rollback(worktreeRecord.id)
                return DagNodeExecutionResult(
                    nodeId = node.id,
                    state = DagNodeState.FAILED,
                    ok = false,
                    message = "completion gate failed: ${gateReport.message}"
                )
            }

            val mergeResult = worktreeService.verifyAndMerge(worktreeRecord.id)
            if (!mergeResult.success) {
                return DagNodeExecutionResult(
                    nodeId = node.id,
                    state = DagNodeState.FAILED,
                    ok = false,
                    message = "verify and merge failed: ${mergeResult.message}"
                )
            }

            return DagNodeExecutionResult(
                nodeId = node.id,
                state = DagNodeState.COMPLETE,
                ok = true,
                message = "node completed and merged successfully"
            )
        } catch (e: Exception) {
            worktreeService.rollback(worktreeRecord.id)
            return DagNodeExecutionResult(
                nodeId = node.id,
                state = DagNodeState.FAILED,
                ok = false,
                message = "execution failed: ${e.message}"
            )
        } finally {
            activeWorktrees.remove(node.id)
        }
    }

    private fun executeNodeActions(node: DagNode, worktreeRoot: Path) {
        when (node.action) {
            DagNodeAction.CREATE_FILE, DagNodeAction.EDIT_FILE -> {
                if (node.actionPayload != null) {
                    val targetPath = node.actionPayload!!
                    worktreeService.writeFile(worktreeRecord.id, targetPath, node.actionPayload!!)
                }
            }
            DagNodeAction.RUN_COMMAND, DagNodeAction.RUN_TEST, DagNodeAction.RUN_BUILD -> {
                if (node.actionPayload != null) {
                    // Execute command in worktree
                    // This would use a shell executor with worktreeRoot as cwd
                }
            }
            DagNodeAction.VERIFY, DagNodeAction.COMPILE_GATE, DagNodeAction.SMOKE_GATE, DagNodeAction.ACCEPTANCE_GATE -> {
                // Run verification in worktree
            }
            DagNodeAction.PROVIDER_CALL -> {
                // Execute provider call in worktree
            }
            else -> Unit
        }
    }

    fun rollbackNode(nodeId: String): Boolean {
        val worktreeResult = activeWorktrees[nodeId]
        if (worktreeResult == null || worktreeResult.record == null) return false
        return worktreeService.rollback(worktreeResult.record!!.id).success
    }

    fun getActiveWorktree(nodeId: String): WorktreeCreateResult? = activeWorktrees[nodeId]
}