/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.orchestration

import atropos.core.agent.AgentQueueService
import atropos.core.agent.AgentJobStore
import atropos.core.dag.DagDefinition
import atropos.core.dag.DagNode
import atropos.core.dag.DagNodeState
import atropos.core.dag.DagStore
import atropos.core.dag.DagNodeExecutionResult
import atropos.core.factory.AppProjectSpec
import atropos.core.factory.FactoryLineage
import atropos.core.factory.FactoryAcceptanceFreeze
import atropos.core.factory.FactoryObligationLoop
import atropos.core.factory.FactoryTerminationBudget
import atropos.core.factory.FactoryObligationSnapshot
import atropos.core.factory.FactoryLoopResult
import atropos.core.factory.FactoryProgressGuard
import atropos.core.memory.LocalMemoryStore
import atropos.core.policy.BoundedAgencyGate
import atropos.core.policy.ExecutionPolicyEngine
import atropos.core.provider.ProviderOnboardingService
import atropos.core.security.RedactionFilter
import atropos.core.territory.TerritoryGrantService
import atropos.core.territory.TerritoryService
import atropos.core.territory.TerritoryStore
import atropos.core.verification.VerifiedCompletionGate
import atropos.core.worktree.IsolatedWorktreeService
import atropos.core.worktree.WorktreeRecord
import atropos.core.worktree.WorktreeRollbackResult
import java.nio.file.Path
import java.security.MessageDigest
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * Autonomous wave executor that drives the factory DAG through typed waves.
 *
 * Replaces the old template-driven generation pipeline. All synthesis originates
 * from the Wave-0 ContractLatticeSynthesizer; no static templates remain.
 */
class AutonomousWaveOrchestrator(
    private val repoRoot: Path,
    private val dagStore: DagStore,
    private val executionService: DagExecutionService,
    private val worktreeService: IsolatedWorktreeService,
    private val territoryService: TerritoryGrantService,
    private val completionGate: VerifiedCompletionGate,
    private val progressGuard: FactoryProgressGuard,
    private val agentQueue: AgentQueueService,
    private val agentJobStore: AgentJobStore,
    private val memoryStore: LocalMemoryStore,
    private val onboarding: ProviderOnboardingService,
    private val agencyGate: BoundedAgencyGate,
    private val contractSynthesizer: ContractLatticeSynthesizer
) {
    private val activeWorktrees = ConcurrentHashMap<String, WorktreeRecord>()

    companion object {
        fun createDefault(repoRoot: Path): AutonomousWaveOrchestrator {
            val dagStore = DagStore(repoRoot)
            val worktreeService = IsolatedWorktreeService(repoRoot)
            val territoryService = TerritoryGrantService(TerritoryService(TerritoryStore(repoRoot)))
            val completionGate = VerifiedCompletionGate()
            val progressGuard = FactoryProgressGuard(dagStore)
            val agentQueue = AgentQueueService(repoRoot)
            val agentJobStore = AgentJobStore(repoRoot)
            val memoryStore = LocalMemoryStore(repoRoot.resolve(".atropos/memory").toFile())
            val onboarding = ProviderOnboardingService()
            val agencyGate = BoundedAgencyGate(
                policyEngine = ExecutionPolicyEngine(repoRoot),
                territory = territoryService
            )
            val contractSynthesizer = ContractLatticeSynthesizer(repoRoot)
            val executionService = DagExecutionService(
                repoRoot = repoRoot,
                worktreeService = worktreeService,
                territoryService = territoryService,
                completionGate = completionGate
            )
            return AutonomousWaveOrchestrator(
                repoRoot = repoRoot,
                dagStore = dagStore,
                executionService = executionService,
                worktreeService = worktreeService,
                territoryService = territoryService,
                completionGate = completionGate,
                progressGuard = progressGuard,
                agentQueue = agentQueue,
                agentJobStore = agentJobStore,
                memoryStore = memoryStore,
                onboarding = onboarding,
                agencyGate = agencyGate,
                contractSynthesizer = contractSynthesizer
            )
        }
    }

    /**
     * Execute a wave request through the full autonomous cycle.
     */
    fun execute(request: WaveExecutionRequest): WaveExecutionResult {
        val startTime = Instant.now()
        val requestId = request.requestId

        memoryStore.rememberDetailed(
            kind = atropos.core.memory.MemoryKind.NOTE,
            title = "wave execution started: $requestId",
            body = "prompt=${request.prompt.take(200)} target=${request.targetDirectory}",
            tags = listOf("wave", "start", "orchestrator"),
            subjectType = "wave_execution",
            subjectId = requestId
        )

        // Phase 1: Synthesize the DAG from the contract lattice
        val dag = contractSynthesizer.synthesize(request)
        dagStore.writeDag(dag)

        // Phase 2: Execute waves until settled
        val freeze = FactoryAcceptanceFreeze.create(
            promptSha256 = request.lineage.promptSha256 ?: "",
            researchSha256 = request.lineage.researchSha256 ?: "",
            atomIds = request.plannedAtomIds,
            promptSpans = request.lineage.promptSpans ?: ""
        )

        val obligationLoop = FactoryObligationLoop(dagStore, FactoryTerminationBudget(
            maxExecutionWaves = request.budget.maxExecutionWaves
        ))

        val executeWave: (List<DagNode>) -> Set<String> = { readyNodes ->
            executeWaveBatch(readyNodes, request, freeze)
        }

        val loopResult = obligationLoop.executeUntilSettled(dag.id, freeze, executeWave)

        // Phase 3: Verify completion and collect evidence
        val finalDag = dagStore.readDag(dag.id)
        val result = if (finalDag != null) {
            collectResults(finalDag, loopResult, request)
        } else {
            WaveExecutionResult(
                requestId = requestId,
                success = false,
                completedAtoms = emptyList(),
                failedAtoms = listOf(dag.id),
                blockedAtoms = emptyList(),
                message = "DAG vanished after execution",
                evidencePaths = emptyList(),
                gitCommit = null,
                treeSha256 = null
            )
        }

        memoryStore.rememberDetailed(
            kind = atropos.core.memory.MemoryKind.NOTE,
            title = "wave execution finished: $requestId",
            body = "success=${result.success} completed=${result.completedAtoms.size} failed=${result.failedAtoms.size} blocked=${result.blockedAtoms.size} duration=${Instant.now().toEpochMilli() - startTime.toEpochMilli()}ms",
            tags = listOf("wave", if (result.success) "success" else "failure", "orchestrator"),
            subjectType = "wave_execution",
            subjectId = requestId
        )

        return result
    }

    private fun executeWaveBatch(
        readyNodes: List<DagNode>,
        request: WaveExecutionRequest,
        freeze: FactoryAcceptanceFreeze
    ): Set<String> {
        // Verify territory disjointness before parallel execution
        val disjoint = enforceTerritoryDisjointness(readyNodes)
        if (!disjoint) {
            memoryStore.rememberFailure(
                subjectType = "territory_violation",
                subjectId = request.requestId,
                title = "wave territory overlap",
                body = "ready nodes have overlapping territories; wave aborted",
                tags = listOf("wave", "territory", "aborted")
            )
            return emptySet()
        }

        // Create isolated worktrees for each node
        val worktreeResults = mutableMapOf<String, WorktreeRecord>()
        for (node in readyNodes) {
            val result = worktreeService.createWorktree(jobId = node.id, territory = node.territory)
            if (!result.ok || result.record == null) {
                worktreeResults.values.forEach { worktreeService.rollback(it.id) }
                return emptySet()
            }
            worktreeResults[node.id] = result.record!!
            activeWorktrees[node.id] = result.record!!
        }

        // Execute each node in its isolated worktree
        val completedNodeIds = mutableSetOf<String>()
        for (node in readyNodes) {
            val worktree = worktreeResults[node.id]!!
            val nodeResult = executeNodeInWorktree(node, worktree, request, freeze)
            if (nodeResult.ok) {
                completedNodeIds.add(node.id)
            }
        }

        // Merge successful worktrees back
        for (nodeId in completedNodeIds) {
            val worktree = worktreeResults[nodeId]
            val mergeResult = worktreeService.verifyAndMerge(worktree.id)
            if (!mergeResult.ok) {
                memoryStore.rememberFailure(
                    subjectType = "merge_failure",
                    subjectId = request.requestId,
                    title = "worktree merge failed for node $nodeId",
                    body = mergeResult.message,
                    tags = listOf("wave", "merge", "failed")
                )
                worktreeService.rollback(worktree.id)
                completedNodeIds.remove(nodeId)
            }
        }

        // Cleanup worktrees
        for (worktree in worktreeResults.values) {
            activeWorktrees.remove(worktree.jobId)
            if (!(worktree.verified && worktree.mergedBack)) {
                worktreeService.removeWorktree(worktree.id)
            }
        }

        return completedNodeIds
    }

    private fun executeNodeInWorktree(
        node: DagNode,
        worktree: WorktreeRecord,
        request: WaveExecutionRequest,
        freeze: FactoryAcceptanceFreeze
    ): DagNodeExecutionResult {
        if (node.state != DagNodeState.READY && node.state != DagNodeState.PENDING) {
            return DagNodeExecutionResult(node.id, node.state, false, "node already in state ${node.state}")
        }

        try {
            // Dispatch to contract synthesizer for actual code generation
            val synthesisResult = contractSynthesizer.synthesizeNode(node, worktree.worktreePath, request)
            if (!synthesisResult.success) {
                worktreeService.rollback(worktree.id)
                return DagNodeExecutionResult(node.id, DagNodeState.FAILED, false, synthesisResult.message)
            }

            // Run completion gate verification
            val gateReport = completionGate.evaluateNode(node)
            if (!gateReport.canComplete) {
                worktreeService.rollback(worktree.id)
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
                message = "node completed successfully",
                result = synthesisResult.message
            )
        } catch (e: Exception) {
            worktreeService.rollback(worktree.id)
            return DagNodeExecutionResult(
                nodeId = node.id,
                state = DagNodeState.FAILED,
                ok = false,
                message = "execution failed: ${e.message}"
            )
        }
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

    private fun collectResults(
        dag: DagDefinition,
        loopResult: FactoryLoopResult,
        request: WaveExecutionRequest
    ): WaveExecutionResult {
        val completed = dag.nodes.filter { it.state == DagNodeState.COMPLETE }.map { it.id }.sorted()
        val failed = dag.nodes.filter { it.state == DagNodeState.FAILED }.map { it.id }.sorted()
        val blocked = dag.nodes.filter { it.state == DagNodeState.BLOCKED }.map { it.id }.sorted()

        val success = failed.isEmpty() && blocked.isEmpty() && completed.isNotEmpty()
        val message = if (success) {
            "wave execution completed: ${completed.size} atoms verified"
        } else {
            "wave execution incomplete: ${failed.size} failed, ${blocked.size} blocked, ${completed.size} completed"
        }

        val evidencePaths = listOf(request.targetDirectory.resolve(".atropos/evidence/app-manifest.txt").toString())
        val gitCommit = runCatching {
            atropos.core.worktree.BoundedGitWorktreeCommandRunner().run(
                atropos.core.worktree.GitWorktreeOperation.REV_PARSE_HEAD,
                request.targetDirectory
            ).output.trim()
        }.getOrNull()
        val treeSha256 = runCatching {
            atropos.core.factory.AppFileHelper.treeDigest(
                completed.associateWith { request.targetDirectory.resolve(it).toString() }
                    .mapValues { atropos.core.factory.AppFileHelper.sha256(java.nio.file.Paths.get(it.value)) }
            )
        }.getOrNull()

        return WaveExecutionResult(
            requestId = request.requestId,
            success = success,
            completedAtoms = completed,
            failedAtoms = failed,
            blockedAtoms = blocked,
            message = message,
            evidencePaths = evidencePaths,
            gitCommit = gitCommit,
            treeSha256 = treeSha256
        )
    }

    private fun sha256(input: String): String {
        return MessageDigest.getInstance("SHA-256").digest(input.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}