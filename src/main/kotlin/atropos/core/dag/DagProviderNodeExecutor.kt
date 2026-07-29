package atropos.core.dag

import atropos.core.agent.AgentService
import atropos.core.memory.LocalMemoryStore
import atropos.core.planning.NodeResult
import atropos.core.provider.CodebaseContextPacker
import atropos.core.provider.SourceBinding
import atropos.core.provider.SourcePackRequest
import atropos.core.provider.SourcePackResult
import java.nio.file.Path

class DagProviderNodeExecutor(
    private val repoRoot: Path,
    private val agentService: AgentService,
    private val memoryStore: LocalMemoryStore,
    private val finisher: DagNodeFinisher,
    private val contextPacker: CodebaseContextPacker = CodebaseContextPacker(repoRoot)
) {
    fun execute(node: DagNode, original: DagNode, store: DagStore): DagNodeExecutionResult {
        val running = store.writeNode(node.copy(state = DagNodeState.RUNNING))
        val task = original.actionPayload ?: original.label
        if (original.territory.isEmpty()) {
            val reason = "provider call degraded: no declared territory for bounded source context pack"
            memoryStore.rememberDetailed(
                kind = atropos.core.memory.MemoryKind.SESSION,
                title = "DAG provider call degraded: ${original.id}",
                body = reason,
                tags = listOf("dag", "provider", "degraded", "blocked", "context-pack"),
                subjectType = "dag_node",
                subjectId = original.id
            )
            finisher.complete(
                running,
                NodeResult(original.id, false, reason, DagNodeState.BLOCKED, failureReason = reason)
            )
            return DagNodeExecutionResult(original.id, DagNodeState.BLOCKED, false, reason)
        }
        val pack = when (val result = contextPacker.pack(SourcePackRequest(SourceBinding.localPath(repoRoot), original.territory))) {
            is SourcePackResult.Packed -> result.pack
            is SourcePackResult.Refused -> {
                val reason = "provider call degraded: ${result.reason}"
                memoryStore.rememberDetailed(
                    kind = atropos.core.memory.MemoryKind.SESSION,
                    title = "DAG provider call degraded: ${original.id}",
                    body = reason,
                    tags = listOf("dag", "provider", "degraded", "blocked", "context-pack"),
                    subjectType = "dag_node",
                    subjectId = original.id
                )
                finisher.complete(
                    running,
                    NodeResult(original.id, false, reason, DagNodeState.BLOCKED, failureReason = reason)
                )
                return DagNodeExecutionResult(original.id, DagNodeState.BLOCKED, false, reason)
            }
        }
        return try {
            val taskWithPack = "$task\n\n${pack.text}"
            val answer = agentService.ask("groq", taskWithPack)
            if (answer.providerName == "local_fallback" || answer.failureSummary != null || !answer.contextAttested || answer.sourcePackId.isNullOrBlank()) {
                val reason = answer.failureSummary ?: "provider call degraded to local fallback; no attested provider result"
                memoryStore.rememberDetailed(
                    kind = atropos.core.memory.MemoryKind.SESSION,
                    title = "DAG provider call degraded: ${original.id}",
                    body = reason,
                    tags = listOf("dag", "provider", "degraded", "blocked"),
                    subjectType = "dag_node",
                    subjectId = original.id
                )
                finisher.complete(
                    running,
                    NodeResult(original.id, false, reason, DagNodeState.BLOCKED, failureReason = reason)
                )
                DagNodeExecutionResult(original.id, DagNodeState.BLOCKED, false, reason)
            } else {
                memoryStore.rememberDetailed(
                    kind = atropos.core.memory.MemoryKind.BATCH,
                    title = "DAG provider call attested: ${original.id}",
                    body = "provider=${answer.providerName} task=${task.take(100)} sourcePack=${pack.id} fetchReceipt=${pack.fetchReceipt.id}",
                    tags = listOf("dag", "provider", "attested", "advisory"),
                    subjectType = "dag_node",
                    subjectId = original.id
                )
                finisher.complete(
                    running,
                    NodeResult(original.id, true, "attested provider advisory completed: ${answer.providerName}", DagNodeState.COMPLETE, result = answer.answerText.take(2000))
                )
                DagNodeExecutionResult(original.id, DagNodeState.COMPLETE, true, "attested provider advisory completed: ${answer.providerName}")
            }
        } catch (e: Exception) {
            finisher.fail(running, original, e.message ?: "provider call failed")
        }
    }
}
