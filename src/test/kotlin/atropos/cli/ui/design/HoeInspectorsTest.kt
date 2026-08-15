/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.ui.design

import atropos.core.phase20.ImprovementProposal
import atropos.core.phase20.MetricDeclaration
import atropos.core.phase20.ObservationSeverity
import atropos.core.phase20.RuntimeObservation
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertTrue

class HoeInspectorsTest {

    @Test
    fun `inspectors render structural summaries`() {
        val obs = RuntimeObservation(
            id = "obs-123",
            timestamp = Instant.now(),
            runtimeId = "run-456",
            projectId = "p1",
            goalId = "g1",
            nodeId = "n1",
            authorityFingerprint = "auth1",
            environmentFingerprint = "env1",
            exitCode = 0,
            boundedOutput = "output",
            artifactHashes = listOf("hash"),
            frequency = 1,
            severity = ObservationSeverity.FAILURE
        )

        assertTrue(RuntimeInspector.inspectRuntime(obs).contains("obs-123"))
        assertTrue(AgentInspector.inspectAgent("agent-1", "goal-1").contains("goal-1"))
        assertTrue(ProviderInspector.inspectProvider("prov-1", 120L, 0.95).contains("95.0%"))

        val proposal = ImprovementProposal(
            id = "prop-1",
            proposedBy = "operator",
            summary = "Refactoring",
            necessity = listOf("hash"),
            baseline = "base",
            target = "target",
            guardrails = listOf("guard"),
            territory = listOf("src"),
            risk = "low",
            rollback = "revert",
            metric = MetricDeclaration("metric", 1.0, 0.0, true),
            createdAt = Instant.now()
        )
        assertTrue(PolicyInspector.inspectPolicy(proposal, "PASS").contains("prop-1"))
        assertTrue(SourceAuthorityInspector.inspectAuthority("doc.txt", "hash-abc").contains("hash-abc"))
        assertTrue(RecoveryInspector.inspectRecovery(5, true).contains("restarts=5"))
    }
}
