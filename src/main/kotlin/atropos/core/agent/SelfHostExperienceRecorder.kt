package atropos.core.agent

import atropos.core.dag.DagDefinition
import atropos.core.dag.DagNodeState
import atropos.core.dag.DagNodeExecutionResult
import atropos.core.memory.LocalMemoryStore
import atropos.core.provider.ContextEnvelope

class SelfHostExperienceRecorder(
    private val memoryStore: LocalMemoryStore
) {
    fun record(
        goalId: String,
        record: GoalRunRecord,
        envelope: ContextEnvelope,
        cradleVerification: SelfHostCradleVerificationResult,
        dag: DagDefinition?,
        nodeResult: DagNodeExecutionResult
    ) {
        val experienceBody = buildString {
            appendLine("goal: $goalId")
            appendLine("phase: ${record.activePhase ?: "unknown"}")
            appendLine("attestation: ${envelope.canonicalContextHash}")
            appendLine("cradle verification: ${if (cradleVerification.passed) "passed" else "failed"}")
            appendLine("DAG: ${dag?.nodes?.count { it.state == DagNodeState.COMPLETE } ?: 0}/${dag?.nodes?.size ?: 0} completed, ${dag?.nodes?.count { it.state == DagNodeState.FAILED } ?: 0} failed, ${dag?.nodes?.count { it.state == DagNodeState.BLOCKED } ?: 0} blocked")
            appendLine("message: ${nodeResult.message}")
            appendLine("  node ${nodeResult.nodeId}: ${nodeResult.ok} ${nodeResult.message.take(80)}")
        }
        memoryStore.rememberDetailed(
            kind = atropos.core.memory.MemoryKind.BATCH,
            title = "self-host DAG evaluation: ${record.activePhase ?: goalId}",
            body = experienceBody.toString(),
            tags = listOf("selfhost", "dag", "evaluation", "attested", if (nodeResult.ok) "success" else "failure"),
            subjectType = "selfhost_dag_eval",
            subjectId = goalId
        )
    }
}
