package atropos.core.agent

import atropos.core.dag.DagNode
import atropos.core.dag.DagNodeExecutionResult
import atropos.core.dag.DagNodeState
import atropos.core.provider.ContextEnvelope

data class SelfHostCradleVerificationResult(
    val passed: Boolean,
    val evidence: String,
    val failureReason: String? = null
)

/**
 * Lightweight verification for one-node cradle advances.
 *
 * This is deliberately narrower than VerifiedCompletionGate. It verifies the
 * runtime facts produced by a single bounded node advance: context identity,
 * stable context hash, selected-node match, and a non-failed execution result.
 * Full completion gates still belong at promote and multi-node boundaries.
 */
class SelfHostCradleVerificationGate {
    fun verify(
        node: DagNode,
        envelope: ContextEnvelope,
        result: DagNodeExecutionResult
    ): SelfHostCradleVerificationResult {
        if (envelope.systemIdentity != "ATROPOS") {
            return failed("context identity mismatch: ${envelope.systemIdentity}")
        }
        if (envelope.canonicalContextHash.isBlank()) {
            return failed("context hash missing")
        }
        if (envelope.nodeId != node.id) {
            return failed("context node mismatch: expected=${node.id} observed=${envelope.nodeId}")
        }
        if (result.nodeId != node.id) {
            return failed("execution node mismatch: expected=${node.id} observed=${result.nodeId}")
        }
        if (!result.ok || result.state == DagNodeState.FAILED || result.state == DagNodeState.BLOCKED) {
            return failed("node did not pass: state=${result.state} message=${result.message}")
        }
        return SelfHostCradleVerificationResult(
            passed = true,
            evidence = "cradle_verified node=${node.id} state=${result.state} hash=${envelope.canonicalContextHash}"
        )
    }

    private fun failed(reason: String): SelfHostCradleVerificationResult =
        SelfHostCradleVerificationResult(
            passed = false,
            evidence = "cradle_verification_failed reason=$reason",
            failureReason = reason
        )
}
