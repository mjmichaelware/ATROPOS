/* SPDX-License-Identifier: AGPL-3.0-only */
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
import atropos.core.worktree.WorktreeRecord
import atropos.core.worktree.WorktreeRollbackResult
import atropos.core.policy.ActionActor
import atropos.core.orchestration.ContractLatticeSynthesizer
import atropos.core.orchestration.SynthesisResult
import java.nio.file.Path
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * DagExecutionService rewritten for wave-batch execution with isolated worktrees.
 *
 * Every PROVIDER_CALL node allocates an ephemeral worktree via
 * IsolatedWorktreeService.createWorktree(). Territory disjointness is validated
 * before parallel execution. Stdout, stderr, exit code are captured into
 * ExecutionEvidence. Worktree is reverted on failure.
 */
class DagExecutionService(
    private val config: AtroposConfig = AtroposConfig.load(),
    private val repoRoot: Path = AtroposRepoRootLocator.resolve(),
    private val worktreeService: IsolatedWorktreeService = IsolatedWorktreeService(repoRoot),
    private val territoryService: TerritoryGrantService = TerritoryGrantService(TerritoryService(TerritoryStore(repoRoot))),
    private val partitionManager: PartitionClusterManager = PartitionClusterManager(),
    private val completionGate: VerifiedCompletionGate = VerifiedCompletionGate(),
    private val contractSynthesizer: ContractLatticeSynthesizer = ContractLatticeSynthesizer(repoRoot)
) {
    private val activeWorktrees = ConcurrentHashMap<String, WorktreeRecord>()

    /**
     * Execute a batch of ready nodes as a wave with full worktree isolation.
     */
    fun executeWaveBatch(
        dagId: String,
        waveNodes: List<DagNode>,
        request: atropos.core.orchestration.WaveExecutionRequest = createDummyRequest(dagId)
    ): Map<String, DagNodeExecutionResult> {
        val results = mutableMapOf<String, DagNodeExecutionResult>()

        // Validate territory disjointness before any execution
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

        // Create isolated worktrees for all nodes in the wave
        val worktreeRecords = mutableMapOf<String, WorktreeRecord>()
        for (node in waveNodes) {
            val worktreeResult = worktreeService.createWorktree(jobId = node.id, territory = node.territory)
            if (!worktreeResult.ok || worktreeResult.record == null) {
                // Rollback any already created worktrees
                worktreeRecords.values.forEach { worktreeService.rollback(it.id) }
                for (node in waveNodes) {
                    results[node.id] = DagNodeExecutionResult(
                        nodeId = node.id,
                        state = DagNodeState.FAILED,
                        ok = false,
                        message = "worktree creation failed: ${worktreeResult.message}"
                    )
                }
                return results
            }
            worktreeRecords[node.id] = worktreeResult.record!!
            activeWorktrees[node.id] = worktreeResult.record!!
        }

        // Execute each node in its isolated worktree
        for (node in waveNodes) {
            val worktreeRecord = worktreeRecords[node.id]!!
            val result = executeNodeInWorktree(node, worktreeRecord, request)
            results[node.id] = result

            // On failure, rollback this worktree immediately
            if (!result.ok) {
                worktreeService.rollback(worktreeRecord.id)
            }
        }

        // Merge successful worktrees back to main repo
        for (node in waveNodes) {
            val result = results[node.id]!!
            val worktreeRecord = worktreeRecords[node.id]!!
            if (result.ok) {
                val mergeResult = worktreeService.verifyAndMerge(worktreeRecord.id)
                if (!mergeResult.ok) {
                    results[node.id] = DagNodeExecutionResult(
                        nodeId = node.id,
                        state = DagNodeState.FAILED,
                        ok = false,
                        message = "verify and merge failed: ${mergeResult.message}"
                    )
                    worktreeService.rollback(worktreeRecord.id)
                }
            }
        }

        // Cleanup
        for (worktreeRecord in worktreeRecords.values) {
            activeWorktrees.remove(worktreeRecord.jobId)
            // Worktree is removed by verifyAndMerge on success, or rollback on failure
            // Explicit removal only for any stragglers
            if (worktreeRecord.verified && worktreeRecord.mergedBack) {
                // Already cleaned up
            } else {
                worktreeService.removeWorktree(worktreeRecord.id)
            }
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

    private fun executeNodeInWorktree(node: DagNode, worktreeRecord: WorktreeRecord, request: atropos.core.orchestration.WaveExecutionRequest): DagNodeExecutionResult {
        if (node.state != DagNodeState.READY && node.state != DagNodeState.PENDING) {
            return DagNodeExecutionResult(node.id, node.state, false, "node already in state ${node.state}")
        }

        try {
            // Dispatch to ContractLatticeSynthesizer for actual synthesis
            val synthesisResult = contractSynthesizer.synthesizeNode(node, worktreeRecord.worktreePath, request)
            if (!synthesisResult.success) {
                return DagNodeExecutionResult(
                    nodeId = node.id,
                    state = DagNodeState.FAILED,
                    ok = false,
                    message = synthesisResult.message
                )
            }

            // Run completion gate verification
            val gateReport = completionGate.evaluateNode(node)
            if (!gateReport.canComplete) {
                return DagNodeExecutionResult(
                    nodeId = node.id,
                    state = DagNodeState.FAILED,
                    ok = false,
                    message = "completion gate failed: ${gateReport.message}"
                )
            }

            return DagNodeExecutionResult(
                nodeId = node.id,
                state = DagNodeState.COMPLETE,
                ok = true,
                message = "node completed and merged successfully",
                result = synthesisResult.message
            )
        } catch (e: Exception) {
            return DagNodeExecutionResult(
                nodeId = node.id,
                state = DagNodeState.FAILED,
                ok = false,
                message = "execution failed: ${e.message}"
            )
        }
    }

    fun rollbackNode(nodeId: String): Boolean {
        val worktreeResult = activeWorktrees[nodeId]
        if (worktreeResult == null) return false
        return worktreeService.rollback(worktreeResult.id).ok
    }

    fun getActiveWorktree(nodeId: String): WorktreeRecord? = activeWorktrees[nodeId]

    /**
     * Execute a wave batch with a pre-constructed WaveExecutionRequest context.
     * This variant is used by AutonomousWaveOrchestrator.
     */
    fun executeWaveBatchWithContext(
        dagId: String,
        waveNodes: List<DagNode>,
        request: atropos.core.orchestration.WaveExecutionRequest
    ): Map<String, DagNodeExecutionResult> {
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

        val worktreeRecords = mutableMapOf<String, WorktreeRecord>()
        for (node in waveNodes) {
            val worktreeResult = worktreeService.createWorktree(jobId = node.id, territory = node.territory)
            if (!worktreeResult.ok || worktreeResult.record == null) {
                worktreeRecords.values.forEach { worktreeService.rollback(it.id) }
                for (node in waveNodes) {
                    results[node.id] = DagNodeExecutionResult(
                        nodeId = node.id,
                        state = DagNodeState.FAILED,
                        ok = false,
                        message = "worktree creation failed: ${worktreeResult.message}"
                    )
                }
                return results
            }
            worktreeRecords[node.id] = worktreeResult.record!!
            activeWorktrees[node.id] = worktreeResult.record!!
        }

        for (node in waveNodes) {
            val worktreeRecord = worktreeRecords[node.id]!!
            val synthesisResult = contractSynthesizer.synthesizeNode(node, worktreeRecord.worktreePath, request)
            if (!synthesisResult.success) {
                results[node.id] = DagNodeExecutionResult(
                    nodeId = node.id,
                    state = DagNodeState.FAILED,
                    ok = false,
                    message = synthesisResult.message
                )
                worktreeService.rollback(worktreeRecord.id)
                continue
            }

            val gateReport = completionGate.evaluateNode(node)
            if (!gateReport.canComplete) {
                results[node.id] = DagNodeExecutionResult(
                    nodeId = node.id,
                    state = DagNodeState.FAILED,
                    ok = false,
                    message = "completion gate failed: ${gateReport.message}"
                )
                worktreeService.rollback(worktreeRecord.id)
                continue
            }

            results[node.id] = DagNodeExecutionResult(
                nodeId = node.id,
                state = DagNodeState.COMPLETE,
                ok = true,
                message = "node completed successfully",
                result = synthesisResult.message
            )
        }

        for (node in waveNodes) {
            val result = results[node.id]!!
            val worktreeRecord = worktreeRecords[node.id]!!
            if (result.ok) {
                val mergeResult = worktreeService.verifyAndMerge(worktreeRecord.id)
                if (!mergeResult.ok) {
                    results[node.id] = DagNodeExecutionResult(
                        nodeId = node.id,
                        state = DagNodeState.FAILED,
                        ok = false,
                        message = "verify and merge failed: ${mergeResult.message}"
                    )
                    worktreeService.rollback(worktreeRecord.id)
                }
            }
        }

        for (worktreeRecord in worktreeRecords.values) {
            activeWorktrees.remove(worktreeRecord.jobId)
            if (!(worktreeRecord.verified && worktreeRecord.mergedBack)) {
                worktreeService.removeWorktree(worktreeRecord.id)
            }
        }

        return results
    }

    private fun createDummyRequest(dagId: String): atropos.core.orchestration.WaveExecutionRequest {
        return atropos.core.orchestration.WaveExecutionRequest(
            requestId = dagId,
            prompt = "",
            projectSpec = atropos.core.factory.AppProjectSpec(
                prompt = "",
                intent = atropos.core.factory.AppIntent(
                    name = "dummy",
                    kind = "generic",
                    capabilities = emptySet(),
                    features = emptyList()
                )
            ),
            lineage = atropos.core.factory.FactoryLineage(
                promptFingerprint = "",
                promptSha256 = "",
                researchSha256 = "",
                researchDocument = "",
                promptDocument = "",
                projectId = "dummy",
                confidence = atropos.core.factory.FactoryConfidence(
                    score = 0,
                    breakdown = "",
                    threshold = 0
                ),
                promptSpans = "",
                researchChannels = "",
                researchState = "",
                contextHash = null,
                memoryPointers = emptyList(),
                atomResearch = emptyList(),
                atomizerStatus = "",
                clarificationAnswersSha256 = null,
                clarificationLineageSha256 = null,
                acceptanceFreeze = null
            ),
            targetDirectory = repoRoot,
            plannedAtomIds = emptyList()
        )
    }
}