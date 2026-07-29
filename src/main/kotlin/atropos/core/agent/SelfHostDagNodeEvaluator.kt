package atropos.core.agent

import atropos.core.dag.DagExecutionService
import atropos.core.provider.ContextEnvelope

class SelfHostDagNodeEvaluator(
    private val store: GoalRunStore,
    private val dagService: DagExecutionService,
    private val contextPreflight: SelfHostContextPreflight,
    private val cradleVerificationGate: SelfHostCradleVerificationGate,
    private val experienceRecorder: SelfHostExperienceRecorder,
    private val worktreeNodeExecutor: SelfHostWorktreeNodeExecutor? = null
) {
    fun evaluate(goalId: String, suppliedEnvelope: ContextEnvelope?): SelfHostResult {
        val record = store.resolve(goalId)
            ?: return SelfHostResult(false, "goal not found: $goalId")
        val currentNodeId = record.currentNodeId ?: return SelfHostResult(false, "no current node selected")
        val dagId = record.dagId ?: return SelfHostResult(false, "no DAG assigned")

        val beforeDag = dagService.readDag(dagId)
        val node = beforeDag?.findNode(currentNodeId)
            ?: return SelfHostResult(false, "selected node not found: $currentNodeId")
        val envelope = contextPreflight.canonicalEnvelope(record, node)
        val preflight = contextPreflight.verify(envelope, suppliedEnvelope)
        if (!preflight.passed) {
            val refused = store.update(
                record.copy(evidence = appendEvidence(record.evidence, preflight.evidence))
            )
            return SelfHostResult(false, "context preflight failed: ${preflight.failureReason}", SelfHostGoal(refused, beforeDag))
        }

        val attestationEvidence = "context_attestation system=${envelope.systemIdentity} hash=${envelope.canonicalContextHash} dag=$dagId node=$currentNodeId"
        val attestedRecord = store.update(
            record.copy(evidence = appendEvidence(record.evidence, preflight.evidence, attestationEvidence))
        )

        val nodeResult = if (worktreeNodeExecutor?.canExecute(node) == true) {
            worktreeNodeExecutor.execute(node)
        } else {
            dagService.evaluateNode(dagId, currentNodeId)
        }
        val cradleVerification = cradleVerificationGate.verify(node, envelope, nodeResult)
        val dag = dagService.readDag(dagId)
        val recordWithCradleEvidence = store.resolve(attestedRecord.id) ?: attestedRecord
        val executionEvidence = "node_execution node=${nodeResult.nodeId} ok=${nodeResult.ok} state=${nodeResult.state} result=${nodeResult.result ?: nodeResult.message}"
        val evidenceRecord = store.update(
            recordWithCradleEvidence.copy(
                evidence = appendEvidence(recordWithCradleEvidence.evidence, executionEvidence, cradleVerification.evidence)
            )
        )

        experienceRecorder.record(goalId, record, envelope, cradleVerification, dag, nodeResult)

        if (!cradleVerification.passed) {
            return SelfHostResult(false, "cradle verification failed: ${cradleVerification.failureReason}", SelfHostGoal(evidenceRecord, dag))
        }
        return SelfHostResult(nodeResult.ok, "DAG node evaluation: ${nodeResult.message}", SelfHostGoal(evidenceRecord, dag))
    }

    private fun appendEvidence(existing: List<String>, vararg entries: String): List<String> =
        (existing + entries)
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .takeLast(40)
}
