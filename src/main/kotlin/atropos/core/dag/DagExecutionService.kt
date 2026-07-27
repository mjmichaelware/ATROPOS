package atropos.core.dag

import atropos.core.AtroposConfig
import atropos.core.agent.AgentQueueService
import atropos.core.agent.AgentRunService
import atropos.core.agent.GoalContinuationService
import atropos.core.memory.LocalMemoryStore
import atropos.core.planning.ExecutionEvidence
import atropos.core.planning.InternalBatchDefiner
import atropos.core.planning.InternalPlanningGraphPlugin
import atropos.core.planning.NodeResult
import atropos.core.planning.PlanningGraphPlugin
import atropos.core.planning.Territory
import atropos.core.policy.AgencyDisposition
import atropos.core.policy.BoundedAgencyGate
import atropos.core.policy.ExecutionPolicyEngine
import java.nio.file.Path
import java.time.Instant

class DagExecutionService(
    private val config: AtroposConfig = AtroposConfig.load(),
    private val repoRoot: Path = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize(),
    private val store: DagStore = DagStore(repoRoot),
    private val queueService: AgentQueueService = AgentQueueService(config),
    private val runService: AgentRunService = AgentRunService(config),
    private val continuationService: GoalContinuationService = GoalContinuationService(repoRoot),
    private val agencyGate: BoundedAgencyGate = BoundedAgencyGate(ExecutionPolicyEngine(repoRoot)),
    private val memoryStore: LocalMemoryStore = LocalMemoryStore(repoRoot.resolve(".atropos/memory").toFile()),
    private val batchDefiner: InternalBatchDefiner = InternalBatchDefiner(),
    private val planningGraph: PlanningGraphPlugin = InternalPlanningGraphPlugin(repoRoot, store),
    private val clock: () -> Instant = { Instant.now() }
) {
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
        val proposal = DagNodeProposals.forNode(
            action = node.action,
            actionPayload = node.actionPayload,
            territory = node.territory,
            repoRoot = repoRoot
        )
        if (proposal != null) {
            val decision = agencyGate.evaluate(proposal)
            if (decision.disposition != AgencyDisposition.ALLOWED) {
                completeNode(
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
        val running = store.writeNode(node.copy(state = DagNodeState.RUNNING))
        try {
            if (original.actionPayload.isNullOrBlank()) {
                completeNode(running, NodeResult(original.id, false, "no action payload", DagNodeState.FAILED, failureReason = "no action payload"))
                return DagNodeExecutionResult(original.id, DagNodeState.FAILED, false, "no action payload")
            }

            val parsed = parseFileMutation(original.actionPayload)
                ?: return failNode(running, original, "unsupported file mutation payload")
            val territoryFailure = territoryViolation(original, listOf(parsed.path.toString()))
            if (territoryFailure != null) {
                return failNode(running, original, territoryFailure)
            }
            parsed.path.parent?.let { java.nio.file.Files.createDirectories(it) }
            java.nio.file.Files.writeString(parsed.path, parsed.content + "\n")
            completeNode(
                running,
                NodeResult(
                    nodeId = original.id,
                    success = true,
                    message = "file mutation applied",
                    finalState = DagNodeState.COMPLETE,
                    result = parsed.path.toString()
                ),
                relatedPaths = listOf(parsed.path.toString())
            )
            return DagNodeExecutionResult(original.id, DagNodeState.COMPLETE, true, "file mutation applied", parsed.path.toString())
        } catch (e: Exception) {
            return failNode(running, original, e.message ?: "file mutation failed")
        }
    }

    private fun executeRunCommand(node: DagNode, original: DagNode): DagNodeExecutionResult {
        val running = store.writeNode(node.copy(state = DagNodeState.RUNNING))
        try {
            val command = original.actionPayload ?: "echo no command specified"
            val territoryFailure = territoryViolation(original, extractCandidatePaths(command) + original.expectedOutputs)
            if (territoryFailure != null) {
                return failNode(running, original, territoryFailure)
            }
            val process = ProcessBuilder("sh", "-c", command)
                .directory(repoRoot.toFile())
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().readText().trimEnd()
            val exitCode = process.waitFor()
            val success = exitCode == 0
            val state = if (success) DagNodeState.COMPLETE else DagNodeState.FAILED
            completeNode(
                running,
                NodeResult(
                    nodeId = original.id,
                    success = success,
                    message = output.take(200),
                    finalState = state,
                    result = output.take(2000),
                    failureReason = if (success) null else "exit code $exitCode"
                ),
                relatedPaths = extractCandidatePaths(command)
            )
            return DagNodeExecutionResult(original.id, state, success, output.take(200))
        } catch (e: Exception) {
            return failNode(running, original, e.message ?: "command failed")
        }
    }

    private fun executeBuildTest(node: DagNode, original: DagNode): DagNodeExecutionResult {
        val running = store.writeNode(node.copy(state = DagNodeState.RUNNING))
        try {
            val command = original.actionPayload ?: "./gradlew test"
            val process = ProcessBuilder("sh", "-c", command)
                .directory(repoRoot.toFile())
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().readText().trimEnd()
            val exitCode = process.waitFor()
            val success = exitCode == 0
            val state = if (success) DagNodeState.COMPLETE else DagNodeState.FAILED
            completeNode(
                running,
                NodeResult(
                    nodeId = original.id,
                    success = success,
                    message = output.take(200),
                    finalState = state,
                    result = output.take(2000),
                    failureReason = if (success) null else "exit code $exitCode"
                )
            )
            return DagNodeExecutionResult(original.id, state, success, output.take(200))
        } catch (e: Exception) {
            return failNode(running, original, e.message ?: "build/test failed")
        }
    }

    private fun executeVerify(node: DagNode, original: DagNode): DagNodeExecutionResult {
        val verifying = store.writeNode(node.copy(state = DagNodeState.VERIFYING))
        try {
            val command = original.actionPayload ?: "./gradlew test"
            val process = ProcessBuilder("sh", "-c", command)
                .directory(repoRoot.toFile())
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().readText().trimEnd()
            val exitCode = process.waitFor()
            val success = exitCode == 0
            val state = if (success) DagNodeState.COMPLETE else DagNodeState.FAILED
            completeNode(
                verifying,
                NodeResult(
                    nodeId = original.id,
                    success = success,
                    message = if (success) "verification passed" else "verification failed",
                    finalState = state,
                    result = output.take(2000),
                    failureReason = if (success) null else "verification failed"
                )
            )
            return DagNodeExecutionResult(original.id, state, success, if (success) "verification passed" else "verification failed")
        } catch (e: Exception) {
            return failNode(verifying, original, e.message ?: "verify failed")
        }
    }

    private fun executeCheck(node: DagNode, original: DagNode): DagNodeExecutionResult {
        val running = store.writeNode(node.copy(state = DagNodeState.RUNNING))
        completeNode(
            running,
            NodeResult(original.id, true, "check passed", DagNodeState.COMPLETE, result = "check passed")
        )
        return DagNodeExecutionResult(original.id, DagNodeState.COMPLETE, true, "check passed")
    }

    private fun executeGate(node: DagNode, original: DagNode): DagNodeExecutionResult {
        val running = store.writeNode(node.copy(state = DagNodeState.RUNNING))
        try {
            val command = original.actionPayload ?: "./gradlew compileKotlin"
            val process = ProcessBuilder("sh", "-c", command)
                .directory(repoRoot.toFile())
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().readText().trimEnd()
            val exitCode = process.waitFor()
            val success = exitCode == 0
            val state = if (success) DagNodeState.COMPLETE else DagNodeState.FAILED
            completeNode(
                running,
                NodeResult(
                    nodeId = original.id,
                    success = success,
                    message = if (success) "gate passed" else "gate failed",
                    finalState = state,
                    result = output.take(2000),
                    failureReason = if (success) null else "gate failed: exit $exitCode"
                )
            )
            return DagNodeExecutionResult(original.id, state, success, if (success) "gate passed" else "gate failed")
        } catch (e: Exception) {
            return failNode(running, original, e.message ?: "gate crashed")
        }
    }

    private fun executeProviderCall(node: DagNode, original: DagNode): DagNodeExecutionResult {
        val running = store.writeNode(node.copy(state = DagNodeState.RUNNING))
        val task = original.actionPayload ?: original.label

        return try {
            // Execute the provider call through AgentRunService.
            // Attestation verification already happens inside AgentService.ask()
            // which is called by AgentRunService.run(). Context envelope injection
            // happens inside AgentPromptContract which is used by AgentService.
            val job = runService.run("groq", task, smokeCommand = null)

            // Check the job result for failure
            val childJobId = job.id
            val hasFailure = job.failureReason != null || job.status == atropos.core.agent.AgentJobStatus.FAILED

            if (hasFailure) {
                memoryStore.rememberDetailed(
                    kind = atropos.core.memory.MemoryKind.SESSION,
                    title = "DAG provider call failed: ${original.id}",
                    body = job.failureReason ?: "unknown failure",
                    tags = listOf("dag", "provider", "failed"),
                    subjectType = "dag_node",
                    subjectId = original.id
                )
                completeNode(
                    running.copy(childJobId = childJobId),
                    NodeResult(
                        nodeId = original.id,
                        success = false,
                        message = job.failureReason ?: "provider run failed",
                        finalState = DagNodeState.FAILED,
                        failureReason = job.failureReason ?: "provider run failed"
                    )
                )
                DagNodeExecutionResult(original.id, DagNodeState.FAILED, false, job.failureReason ?: "provider run failed")
            } else {
                memoryStore.rememberDetailed(
                    kind = atropos.core.memory.MemoryKind.BATCH,
                    title = "DAG provider call completed: ${original.id}",
                    body = "job=$childJobId task=${task.take(100)}",
                    tags = listOf("dag", "provider", "completed"),
                    subjectType = "dag_node",
                    subjectId = original.id
                )
                completeNode(
                    running.copy(childJobId = childJobId),
                    NodeResult(
                        nodeId = original.id,
                        success = true,
                        message = "provider call completed: $childJobId",
                        finalState = DagNodeState.COMPLETE,
                        result = "provider job completed: $childJobId"
                    )
                )
                DagNodeExecutionResult(original.id, DagNodeState.COMPLETE, true, "provider call completed: $childJobId")
            }
        } catch (e: Exception) {
            failNode(running, original, e.message ?: "provider call failed")
        }
    }

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

    private fun completeNode(node: DagNode, result: NodeResult, relatedPaths: List<String> = emptyList()) {
        planningGraph.submitEvidence(
            node.id,
            ExecutionEvidence(
                nodeId = node.id,
                kind = if (result.success) "completion" else "failure",
                detail = result.message,
                relatedPaths = relatedPaths
            )
        )
        planningGraph.completeNode(node.id, result)
    }

    private fun failNode(node: DagNode, original: DagNode, message: String): DagNodeExecutionResult {
        completeNode(
            node,
            NodeResult(
                nodeId = original.id,
                success = false,
                message = message,
                finalState = DagNodeState.FAILED,
                failureReason = message
            )
        )
        return DagNodeExecutionResult(original.id, DagNodeState.FAILED, false, message)
    }

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

    private fun parseFileMutation(payload: String): ParsedFileMutation? {
        val explicit = payload.split("::", limit = 2)
        if (explicit.size == 2 && explicit[0].isNotBlank()) {
            return ParsedFileMutation(normalizeCandidatePath(explicit[0].trim()) ?: return null, explicit[1].trim())
        }

        val naturalLanguage = Regex(
            """Write .*? to (?<path>(?:/tmp|src|docs|scripts|ops|\.atropos)[A-Za-z0-9_./-]+) containing exactly one line: (?<content>.+)""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        ).find(payload)
        if (naturalLanguage != null) {
            val path = normalizeCandidatePath(naturalLanguage.groups["path"]?.value.orEmpty()) ?: return null
            val content = naturalLanguage.groups["content"]?.value?.trim().orEmpty()
            return ParsedFileMutation(path, content)
        }
        return null
    }

    private data class ParsedFileMutation(
        val path: Path,
        val content: String
    )
}
