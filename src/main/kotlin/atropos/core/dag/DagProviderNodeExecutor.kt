package atropos.core.dag

import atropos.core.agent.AgentService
import atropos.core.memory.LocalMemoryStore
import atropos.core.planning.NodeResult

class DagProviderNodeExecutor(
    private val agentService: AgentService,
    private val memoryStore: LocalMemoryStore,
    private val finisher: DagNodeFinisher
) {
    fun execute(node: DagNode, original: DagNode, store: DagStore): DagNodeExecutionResult {
        val running = store.writeNode(node.copy(state = DagNodeState.RUNNING))
        val task = original.actionPayload ?: original.label
        return try {
            val answer = agentService.ask("groq", task)
            if (answer.providerName == "local_fallback" || answer.failureSummary != null) {
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
                    body = "provider=${answer.providerName} task=${task.take(100)}",
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
