package atropos.core.agent

import atropos.core.dag.DagNode
import atropos.core.dag.DagNodeAction
import atropos.core.dag.DagNodeExecutionResult
import atropos.core.dag.DagNodeState
import atropos.core.provider.ContextEnvelope
import java.nio.file.Path
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertTrue

class SelfHostCradleVerificationGateTest {
    private val gate = SelfHostCradleVerificationGate()

    @Test
    fun passes_only_when_context_and_node_execution_match() {
        val node = node("node-1")
        val envelope = envelope("node-1")
        val result = DagNodeExecutionResult("node-1", DagNodeState.COMPLETE, true, "verification passed")

        val verified = gate.verify(node, envelope, result)

        assertTrue(verified.passed, verified.failureReason ?: verified.evidence)
        assertTrue(verified.evidence.contains("cradle_verified"))
    }

    @Test
    fun refuses_context_or_execution_mismatch_without_claiming_completion() {
        val node = node("node-1")
        val wrongContext = gate.verify(
            node,
            envelope("other-node"),
            DagNodeExecutionResult("node-1", DagNodeState.COMPLETE, true, "verification passed")
        )
        val failedExecution = gate.verify(
            node,
            envelope("node-1"),
            DagNodeExecutionResult("node-1", DagNodeState.FAILED, false, "verification failed")
        )

        assertTrue(!wrongContext.passed)
        assertTrue(wrongContext.evidence.contains("cradle_verification_failed"))
        assertTrue(!failedExecution.passed)
        assertTrue(failedExecution.failureReason.orEmpty().contains("node did not pass"))
    }

    private fun node(id: String): DagNode = DagNode(
        id = id,
        label = "probe",
        action = DagNodeAction.VERIFY,
        actionPayload = "git status --short",
        createdAt = Instant.parse("2026-07-27T09:10:00Z"),
        updatedAt = Instant.parse("2026-07-27T09:10:00Z"),
        metaFile = Path.of("unused")
    )

    private fun envelope(nodeId: String): ContextEnvelope = ContextEnvelope(
        repository = "ATROPOS",
        repositoryRoot = "/tmp/ATROPOS",
        branch = "main",
        baselineCommit = "abc123",
        nodeId = nodeId,
        canonicalContextHash = "hash-$nodeId"
    )
}
