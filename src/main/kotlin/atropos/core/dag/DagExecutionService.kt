package atropos.core.dag

import atropos.core.AtroposConfig
import atropos.core.AtroposRepoRootLocator
import atropos.core.agent.AgentContextCollector
import atropos.core.agent.AgentQueueService
import atropos.core.agent.AgentService
import atropos.core.agent.GoalContinuationService
import atropos.core.memory.LocalMemoryStore
import atropos.core.planning.InternalBatchDefiner
import atropos.core.planning.NodeResult
import atropos.core.planning.PlanningGraphPlugin
import atropos.core.planning.PlanningGraphPluginRegistry
import atropos.core.planning.Territory
import atropos.core.policy.ActionActor
import atropos.core.territory.GrantResult
import atropos.core.territory.TerritoryGrantService
import atropos.core.territory.TerritoryService
import atropos.core.policy.AgencyDisposition
import atropos.core.policy.BoundedAgencyGate
import atropos.core.policy.ExecutionPolicyEngine
import java.nio.file.Path
import java.time.Instant

class DagExecutionService(
    private val config: AtroposConfig = AtroposConfig.load(),
    private val repoRoot: Path = AtroposRepoRootLocator.resolve(),
    private val store: DagStore = DagStore(repoRoot),
    private val queueService: AgentQueueService = AgentQueueService(config),
    private val agentService: AgentService = AgentService(config, collector = AgentContextCollector(repoRoot = repoRoot)),
    private val continuationService: GoalContinuationService = GoalContinuationService(repoRoot),
    private val credentialGuard: atropos.core.security.CredentialDiffGuard =
        atropos.core.security.CredentialDiffGuard(),
    private val territoryGrants: TerritoryGrantService =
        TerritoryGrantService(TerritoryService(atropos.core.territory.TerritoryStore(repoRoot))),
    private val agencyGate: BoundedAgencyGate = BoundedAgencyGate(ExecutionPolicyEngine(repoRoot), territoryGrants),
    private val memoryStore: LocalMemoryStore = LocalMemoryStore(repoRoot.resolve(".atropos/memory").toFile()),
    private val batchDefiner: InternalBatchDefiner = InternalBatchDefiner(),
    private val planningGraph: PlanningGraphPlugin = PlanningGraphPluginRegistry(
        fallback = PlanningGraphPluginRegistry.internalFallback(repoRoot, store)
    ).resolve().plugin,
    private val clock: () -> Instant = { Instant.now() }
) {
    private val finisher = DagNodeFinisher(planningGraph)
    private val shellExecutor = DagNodeShellExecutor(repoRoot, store, finisher, ::territoryViolation, ::extractCandidatePaths)
    private val providerNodeExecutor = DagProviderNodeExecutor(repoRoot, agentService, memoryStore, finisher)
    private val fileMutationExecutor = DagNodeFileMutationExecutor(store, finisher, ::normalizeCandidatePath, ::territoryViolation)

    fun createDag(label: String, nodes: List<DagNode>, projectId: String? = null): DagDefinition {
        val dag = store.createDag(label, nodes, projectId)
        memoryStore.rememberDetailed(
            kind = atropos.core.memory.MemoryKind.BATCH,
            title = "DAG created: $label",
            body = "${dag.nodes.size} nodes, id=${dag.id}",
            tags = listOf("dag", "created"),
            subjectType = "dag",
            subjectId = dag.id
        )
        return store.readDag(dag.id) ?: dag
    }

    fun evaluateDag(dagId: String): DagExecutionResult {
        val dag = store.readDag(dagId) ?: return DagExecutionResult(dagId, false, 0, 0, 0, "DAG not found: $dagId")
        val locked = store.tryLock() ?: return DagExecutionResult(dagId, false, 0, 0, 0, "DAG lock held by another instance")

        return locked.use {
            val results = mutableListOf<DagNodeExecutionResult>()
            val readyNodes = batchDefiner.define(dag)

            for (group in readyNodes) {
                for (node in group) {
                    val result = executeNode(node, dag)
                    results.add(result)
                }
            }

            val dagReloaded = store.readDag(dagId)
            if (dagReloaded == null) {
                return@use DagExecutionResult(dagId, false, results.count { it.state == DagNodeState.COMPLETE }, results.count { it.state == DagNodeState.FAILED }, results.count { it.state == DagNodeState.BLOCKED }, "DAG vanished during evaluation", results)
            }

            val completed = dagReloaded.nodes.count { it.state == DagNodeState.COMPLETE }
            val failed = dagReloaded.nodes.count { it.state == DagNodeState.FAILED }
            val blocked = dagReloaded.nodes.count { it.state == DagNodeState.BLOCKED }
            val allDone = completed + failed + blocked + dagReloaded.nodes.count { it.state == DagNodeState.NOT_APPLICABLE || it.state == DagNodeState.CANCELLED }

            DagExecutionResult(
                dagId = dagId,
                ok = allDone == dagReloaded.nodes.size && failed == 0 && blocked == 0,
                completedNodes = completed,
                failedNodes = failed,
                blockedNodes = blocked,
                message = "DAG evaluated: $completed completed, $failed failed, $blocked blocked out of ${dagReloaded.nodes.size}",
                nodeResults = results
            )
        }
    }

    fun evaluateNode(dagId: String, nodeId: String): DagNodeExecutionResult {
        val dag = store.readDag(dagId) ?: return DagNodeExecutionResult(nodeId, DagNodeState.FAILED, false, "DAG not found: $dagId")
        if (dag.findNode(nodeId) == null) {
            return DagNodeExecutionResult(nodeId, DagNodeState.FAILED, false, "node not found in DAG $dagId: $nodeId")
        }
        val locked = store.tryLock() ?: return DagNodeExecutionResult(nodeId, DagNodeState.BLOCKED, false, "DAG lock held by another instance")

        return locked.use {
            val latestDag = store.readDag(dagId) ?: return@use DagNodeExecutionResult(nodeId, DagNodeState.FAILED, false, "DAG vanished during node execution")
            val node = latestDag.findNode(nodeId)
                ?: return@use DagNodeExecutionResult(nodeId, DagNodeState.FAILED, false, "node vanished during execution: $nodeId")
            val ready = latestDag.findReadyNodes().map { it.id }.toSet()
            if (node.id !in ready) {
                return@use DagNodeExecutionResult(node.id, node.state, false, "node is not ready: ${node.state}")
            }
            executeNode(node, latestDag)
        }
    }

    private fun executeNode(node: DagNode, dag: DagDefinition): DagNodeExecutionResult {
        if (node.state != DagNodeState.READY && node.state != DagNodeState.PENDING) {
            return DagNodeExecutionResult(node.id, node.state, false, "node already in state ${node.state}")
        }

        val territory = Territory(
            readPaths = node.territory,
            writePaths = node.territory,
            prohibitedPaths = listOf(".git", ".gradle", "build", ".atropos/secrets")
        )
        val claim = planningGraph.claimNode(node.id, "dag-executor", territory)
        if (!claim.accepted) {
            return DagNodeExecutionResult(node.id, node.state, false, claim.reason ?: "cannot claim node (concurrent execution)")
        }
        val claimed = store.readNode(node.id) ?: return DagNodeExecutionResult(node.id, node.state, false, "claimed node disappeared")

        // Every node that executes anything must be authorised by the single
        // permission authority. Nodes run through `sh -c`, so this is where an
        // unauthorised command is stopped — before any executor is dispatched.
        val nodeActor = ActionActor.HierarchyNode(role = "dag-executor", nodeId = node.id)
        val proposal = DagNodeProposals.forNode(
            action = node.action,
            actionPayload = node.actionPayload,
            territory = node.territory,
            repoRoot = repoRoot,
            actor = nodeActor
        )
        if (proposal != null) {
            // Grant-on-dispatch: the node is handed a slice of the operator's
            // territory, narrowed to what it declared and bound to this node.
            // A node that declared nothing gets nothing and cannot run — it
            // would otherwise execute unbounded.
            if (node.action != DagNodeAction.PROVIDER_CALL) {
                when (val grant = territoryGrants.grantToNode(ActionActor.HumanOwner, nodeActor, node.territory)) {
                    is GrantResult.Refused -> {
                        finisher.complete(
                            claimed,
                            NodeResult(
                                nodeId = claimed.id,
                                success = false,
                                message = grant.reason,
                                finalState = DagNodeState.BLOCKED,
                                failureReason = grant.reason
                            )
                        )
                        return DagNodeExecutionResult(node.id, DagNodeState.BLOCKED, false, grant.reason)
                    }
                    is GrantResult.Granted -> Unit
                }
            }

            val decision = agencyGate.evaluate(proposal)
            if (decision.disposition != AgencyDisposition.ALLOWED) {
                finisher.complete(
                    claimed,
                    NodeResult(
                        nodeId = claimed.id,
                        success = false,
                        message = decision.reason,
                        finalState = DagNodeState.BLOCKED,
                        failureReason = decision.reason
                    )
                )
                return DagNodeExecutionResult(node.id, DagNodeState.BLOCKED, false, decision.reason)
            }
        }

        // Execute based on action type
        return when (node.action) {
            DagNodeAction.CREATE_FILE,
            DagNodeAction.EDIT_FILE -> executeFileMutation(claimed, node)
            DagNodeAction.RUN_COMMAND -> executeRunCommand(claimed, node)
            DagNodeAction.RUN_TEST,
            DagNodeAction.RUN_BUILD -> executeBuildTest(claimed, node)
            DagNodeAction.VERIFY -> executeVerify(claimed, node)
            DagNodeAction.POLICY_CHECK,
            DagNodeAction.SECRET_CHECK,
            DagNodeAction.TERRITORY_CHECK -> executeCheck(claimed, node)
            DagNodeAction.COMPILE_GATE,
            DagNodeAction.SMOKE_GATE,
            DagNodeAction.ACCEPTANCE_GATE -> executeGate(claimed, node)
            DagNodeAction.PROVIDER_CALL -> executeProviderCall(claimed, node)
        }
    }

    private fun executeFileMutation(node: DagNode, original: DagNode): DagNodeExecutionResult {
        fileMutationExecutor.execute(node, original)

    private fun executeRunCommand(node: DagNode, original: DagNode): DagNodeExecutionResult =
        shellExecutor.runCommand(node, original)


    private fun executeBuildTest(node: DagNode, original: DagNode): DagNodeExecutionResult =
        shellExecutor.buildTest(node, original)


    private fun executeVerify(node: DagNode, original: DagNode): DagNodeExecutionResult =
        shellExecutor.verify(node, original)


    private val checkEvaluator by lazy {
        DagNodeCheckEvaluator(repoRoot, agencyGate, territoryGrants, credentialGuard)
    }

    /**
     * Runs a check node.
     *
     * These three actions used to complete unconditionally with "check passed".
     * The verdict now comes from [DagNodeCheckEvaluator], and a failing check
     * fails the node rather than reporting success.
     */
    private fun executeCheck(node: DagNode, original: DagNode): DagNodeExecutionResult {
        val running = store.writeNode(node.copy(state = DagNodeState.RUNNING))
        val outcome = checkEvaluator.evaluate(
            original,
            ActionActor.HierarchyNode(role = "dag-executor", nodeId = original.id)
        )

        val state = if (outcome.passed) DagNodeState.COMPLETE else DagNodeState.FAILED
        finisher.complete(
            running,
            NodeResult(
                nodeId = original.id,
                success = outcome.passed,
                message = outcome.detail,
                finalState = state,
                result = outcome.detail,
                failureReason = if (outcome.passed) null else outcome.detail
            )
        )
        return DagNodeExecutionResult(original.id, state, outcome.passed, outcome.detail)
    }

    private fun executeGate(node: DagNode, original: DagNode): DagNodeExecutionResult =
        shellExecutor.gate(node, original)


    private fun executeProviderCall(node: DagNode, original: DagNode): DagNodeExecutionResult =
        providerNodeExecutor.execute(node, original, store)


    fun recoverStaleClaims(): Int {
        val dags = store.listDags()
        var recovered = 0
        val now = clock()
        for (dag in dags) {
            for (node in dag.nodes) {
                if (node.state == DagNodeState.CLAIMED && node.claimExpiresAt != null && node.claimExpiresAt.isBefore(now)) {
                    store.writeNode(
                        node.copy(
                            state = DagNodeState.READY,
                            claimToken = null,
                            claimExpiresAt = null,
                            lastMessage = "stale claim recovered"
                        )
                    )
                    recovered++
                }
            }
        }
        return recovered
    }

    fun status(dagId: String): DagStatus? {
        val dag = store.readDag(dagId) ?: return null
        val readyNodes = planningGraph.getReadyNodes(dag.projectId ?: repoRoot.fileName.toString(), dag.id)
        return DagStatus(
            dagId = dagId,
            totalNodes = dag.nodes.size,
            completedNodes = dag.nodes.count { it.state == DagNodeState.COMPLETE },
            failedNodes = dag.nodes.count { it.state == DagNodeState.FAILED },
            blockedNodes = dag.nodes.count { it.state == DagNodeState.BLOCKED },
            pendingNodes = dag.nodes.count { it.state in setOf(DagNodeState.PENDING, DagNodeState.READY) },
            runningNodes = dag.nodes.count { it.state in setOf(DagNodeState.CLAIMED, DagNodeState.RUNNING, DagNodeState.VERIFYING) },
            readyNodes = readyNodes.map { it.nodeId },
            message = "DAG $dagId: ${dag.nodes.size} nodes"
        )
    }

    fun listDags(): List<DagDefinition> = store.listDags()
    fun readDag(dagId: String): DagDefinition? = store.readDag(dagId)
    fun readNode(nodeId: String): DagNode? = store.readNode(nodeId)





    private fun territoryViolation(node: DagNode, candidatePaths: List<String>): String? {
        if (node.territory.isEmpty()) return null
        val normalizedPaths = candidatePaths.mapNotNull { normalizeCandidatePath(it) }.distinct()
        if (normalizedPaths.isEmpty()) {
            return "territory enforcement could not determine affected paths"
        }
        val allowedRoots = node.territory.map { normalizeCandidatePath(it) ?: repoRoot.resolve(it).normalize() }
        val outside = normalizedPaths.filterNot { candidate -> allowedRoots.any { root -> candidate.startsWith(root) } }
        return if (outside.isEmpty()) null else "territory violation: ${outside.joinToString(", ")}"
    }

    private fun normalizeCandidatePath(pathText: String): Path? {
        if (pathText.isBlank()) return null
        return if (pathText.startsWith("/")) Path.of(pathText).normalize() else repoRoot.resolve(pathText).normalize()
    }

    private fun extractCandidatePaths(text: String): List<String> =
        Regex("""(?:/tmp|src|docs|scripts|ops|build|\.atropos|[A-Za-z0-9_.-]+/)[A-Za-z0-9_./-]+""")
            .findAll(text)
            .map { it.value.trimEnd('.', ',', ';', ':') }
            .toList()

}
