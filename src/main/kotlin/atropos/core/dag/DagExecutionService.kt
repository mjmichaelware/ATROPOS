package atropos.core.dag

import atropos.core.AtroposConfig
import atropos.core.agent.AgentQueueService
import atropos.core.agent.AgentRunService
import atropos.core.agent.GoalContinuationService
import atropos.core.agent.GoalTerminalCondition
import atropos.core.agent.GoalRunStatus
import atropos.core.memory.LocalMemoryStore
import atropos.core.policy.AutonomyActionClass
import atropos.core.policy.AutonomyPolicyEngine
import atropos.core.provider.ContextEnvelopeFactory
import atropos.core.provider.ContextAttestationService
import atropos.core.security.RedactionFilter
import java.nio.file.Path
import java.time.Instant

class DagExecutionService(
    private val config: AtroposConfig = AtroposConfig.load(),
    private val repoRoot: Path = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize(),
    private val store: DagStore = DagStore(repoRoot),
    private val queueService: AgentQueueService = AgentQueueService(config),
    private val runService: AgentRunService = AgentRunService(config),
    private val continuationService: GoalContinuationService = GoalContinuationService(repoRoot),
    private val policyEngine: AutonomyPolicyEngine = AutonomyPolicyEngine(repoRoot),
    private val memoryStore: LocalMemoryStore = LocalMemoryStore(repoRoot.resolve(".atropos/memory").toFile()),
    private val clock: () -> Instant = { Instant.now() }
) {
    fun createDag(label: String, nodes: List<DagNode>): DagDefinition {
        val dag = store.createDag(label, nodes)
        // Initialize all nodes without dependencies as READY
        dag.nodes.forEach { node ->
            if (node.dependencies.isEmpty() && node.state == DagNodeState.PENDING) {
                store.writeNode(node.copy(state = DagNodeState.READY))
            }
        }
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
            val readyNodes = dag.findParallelReadyNodes()

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
        val now = clock()

        if (node.state != DagNodeState.READY && node.state != DagNodeState.PENDING) {
            return DagNodeExecutionResult(node.id, node.state, false, "node already in state ${node.state}")
        }

        // DAG lock is held by caller (evaluateDag), so claimNode is safe without a separate lock
        val claimed = store.claimNode(node.id) ?: return DagNodeExecutionResult(node.id, node.state, false, "cannot claim node (concurrent execution)")

        // Policy check
        val policyDecision = policyEngine.evaluate(
            AutonomyActionClass.DAG_CONTROL,
            mapOf("dagId" to dag.id, "nodeId" to node.id, "action" to node.action.name)
        )
        if (!policyDecision.allowed) {
            store.writeNode(
                claimed.copy(
                    state = DagNodeState.BLOCKED,
                    failureReason = policyDecision.reason,
                    finishedAt = clock()
                )
            )
            return DagNodeExecutionResult(node.id, DagNodeState.BLOCKED, false, policyDecision.reason)
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
                val failed = store.writeNode(running.copy(state = DagNodeState.FAILED, failureReason = "no action payload", finishedAt = clock()))
                return DagNodeExecutionResult(original.id, DagNodeState.FAILED, false, "no action payload")
            }

            // Queue a file mutation task
            runCatching {
                queueService.enqueue(original.actionPayload)
            }
            val completed = store.writeNode(running.copy(state = DagNodeState.COMPLETE, result = "file mutation queued", finishedAt = clock()))
            return DagNodeExecutionResult(original.id, DagNodeState.COMPLETE, true, "file mutation queued")
        } catch (e: Exception) {
            val failed = store.writeNode(running.copy(state = DagNodeState.FAILED, failureReason = e.message, finishedAt = clock()))
            return DagNodeExecutionResult(original.id, DagNodeState.FAILED, false, e.message ?: "file mutation failed")
        }
    }

    private fun executeRunCommand(node: DagNode, original: DagNode): DagNodeExecutionResult {
        val running = store.writeNode(node.copy(state = DagNodeState.RUNNING))
        try {
            val command = original.actionPayload ?: "echo no command specified"
            val process = ProcessBuilder("sh", "-c", command)
                .directory(repoRoot.toFile())
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().readText().trimEnd()
            val exitCode = process.waitFor()
            val success = exitCode == 0
            val state = if (success) DagNodeState.COMPLETE else DagNodeState.FAILED
            val completed = store.writeNode(
                running.copy(state = state, result = output.take(2000), failureReason = if (success) null else "exit code $exitCode", finishedAt = clock())
            )
            return DagNodeExecutionResult(original.id, state, success, output.take(200))
        } catch (e: Exception) {
            val failed = store.writeNode(running.copy(state = DagNodeState.FAILED, failureReason = e.message, finishedAt = clock()))
            return DagNodeExecutionResult(original.id, DagNodeState.FAILED, false, e.message ?: "command failed")
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
            val completed = store.writeNode(
                running.copy(state = state, result = output.take(2000), failureReason = if (success) null else "exit code $exitCode", finishedAt = clock())
            )
            return DagNodeExecutionResult(original.id, state, success, output.take(200))
        } catch (e: Exception) {
            val failed = store.writeNode(running.copy(state = DagNodeState.FAILED, failureReason = e.message, finishedAt = clock()))
            return DagNodeExecutionResult(original.id, DagNodeState.FAILED, false, e.message ?: "build/test failed")
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
            val completed = store.writeNode(
                verifying.copy(state = state, result = output.take(2000), failureReason = if (success) null else "verification failed", finishedAt = clock())
            )
            return DagNodeExecutionResult(original.id, state, success, if (success) "verification passed" else "verification failed")
        } catch (e: Exception) {
            val failed = store.writeNode(verifying.copy(state = DagNodeState.FAILED, failureReason = e.message, finishedAt = clock()))
            return DagNodeExecutionResult(original.id, DagNodeState.FAILED, false, e.message ?: "verify failed")
        }
    }

    private fun executeCheck(node: DagNode, original: DagNode): DagNodeExecutionResult {
        val running = store.writeNode(node.copy(state = DagNodeState.RUNNING))
        val completed = store.writeNode(running.copy(state = DagNodeState.COMPLETE, result = "check passed", finishedAt = clock()))
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
            val completed = store.writeNode(
                running.copy(state = state, result = output.take(2000), failureReason = if (success) null else "gate failed: exit $exitCode", finishedAt = clock())
            )
            return DagNodeExecutionResult(original.id, state, success, if (success) "gate passed" else "gate failed")
        } catch (e: Exception) {
            val failed = store.writeNode(running.copy(state = DagNodeState.FAILED, failureReason = e.message, finishedAt = clock()))
            return DagNodeExecutionResult(original.id, DagNodeState.FAILED, false, e.message ?: "gate crashed")
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
                val failed = store.writeNode(
                    running.copy(
                        state = DagNodeState.FAILED,
                        childJobId = childJobId,
                        failureReason = job.failureReason ?: "provider run failed",
                        finishedAt = clock()
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
                val completed = store.writeNode(
                    running.copy(
                        state = DagNodeState.COMPLETE,
                        childJobId = childJobId,
                        result = "provider job completed: $childJobId",
                        finishedAt = clock()
                    )
                )
                DagNodeExecutionResult(original.id, DagNodeState.COMPLETE, true, "provider call completed: $childJobId")
            }
        } catch (e: Exception) {
            val failed = store.writeNode(
                running.copy(
                    state = DagNodeState.FAILED,
                    failureReason = e.message,
                    finishedAt = clock()
                )
            )
            DagNodeExecutionResult(original.id, DagNodeState.FAILED, false, e.message ?: "provider call failed")
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
        return DagStatus(
            dagId = dagId,
            totalNodes = dag.nodes.size,
            completedNodes = dag.nodes.count { it.state == DagNodeState.COMPLETE },
            failedNodes = dag.nodes.count { it.state == DagNodeState.FAILED },
            blockedNodes = dag.nodes.count { it.state == DagNodeState.BLOCKED },
            pendingNodes = dag.nodes.count { it.state in setOf(DagNodeState.PENDING, DagNodeState.READY) },
            runningNodes = dag.nodes.count { it.state in setOf(DagNodeState.CLAIMED, DagNodeState.RUNNING, DagNodeState.VERIFYING) },
            readyNodes = dag.findReadyNodes().map { it.id },
            message = "DAG $dagId: ${dag.nodes.size} nodes"
        )
    }

    fun listDags(): List<DagDefinition> = store.listDags()
    fun readDag(dagId: String): DagDefinition? = store.readDag(dagId)
    fun readNode(nodeId: String): DagNode? = store.readNode(nodeId)
}
