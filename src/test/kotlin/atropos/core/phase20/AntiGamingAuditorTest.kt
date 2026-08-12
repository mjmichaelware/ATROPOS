package atropos.core.phase20

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AntiGamingAuditorTest {
    private val auditor = AntiGamingAuditor()

    @Test
    fun metric_improvement_without_outcome_improvement_is_refused() {
        val proposal = proposal()
        val decision = auditor.audit(
            proposal,
            AntiGamingEvidence(
                observedDeclaredMetric = 2.0,
                outcomeMetric = MetricDeclaration("real outcome", 10.0, 5.0, lowerIsBetter = true),
                observedOutcome = 10.0,
                evidenceHashes = listOf("evidence-hash")
            )
        )

        assertFalse(decision.passed)
        assertTrue(decision.reason.contains("outcome"))
    }

    @Test
    fun both_declared_metric_and_outcome_improvement_are_required() {
        val decision = auditor.audit(
            proposal(),
            AntiGamingEvidence(
                observedDeclaredMetric = 2.0,
                outcomeMetric = MetricDeclaration("real outcome", 10.0, 5.0, lowerIsBetter = true),
                observedOutcome = 4.0,
                evidenceHashes = listOf("evidence-hash")
            )
        )

        assertTrue(decision.passed, decision.reason)
    }

    private fun proposal() = ImprovementProposal(
        id = "proposal-audit",
        proposedBy = "worker",
        summary = "reduce failures",
        necessity = listOf("necessity-hash"),
        baseline = "baseline",
        target = "target",
        guardrails = listOf("no secret writes"),
        territory = listOf("src/main"),
        risk = "bounded",
        rollback = "restore previous snapshot",
        metric = MetricDeclaration("reported failures", 3.0, 1.0, lowerIsBetter = true),
        createdAt = Instant.parse("2026-01-01T00:00:00Z")
    )
}
