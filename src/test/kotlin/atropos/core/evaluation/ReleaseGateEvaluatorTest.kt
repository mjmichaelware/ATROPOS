package atropos.core.evaluation

import atropos.core.verification.CompletionGateReport
import atropos.core.verification.DeterministicVerificationResult
import atropos.core.verification.DiagnosticSeverity
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReleaseGateEvaluatorTest {
    private val evaluator = ReleaseGateEvaluator()

    @Test
    fun existing_report_passes_when_no_blocker_exists() {
        val decision = evaluator.evaluate(report(metricPassed = true))

        assertTrue(decision.accepted)
        assertTrue(decision.reason.contains("passed"))
    }

    @Test
    fun deterministic_or_completion_failure_is_fail_closed() {
        val deterministic = DeterministicVerificationResult(
            listOf(
                atropos.core.verification.DeterministicFinding(
                    invariantId = "test",
                    severity = DiagnosticSeverity.ERROR,
                    file = null,
                    symbolOrLocation = null,
                    evidence = "failed",
                    remediation = "repair",
                    classification = atropos.core.verification.DeterministicClassification.DETERMINISTIC
                )
            )
        )
        val completion = CompletionGateReport("node", false, emptyList(), "gate failed")

        val decision = evaluator.evaluate(report(metricPassed = true), deterministic, completion)

        assertFalse(decision.accepted)
        assertTrue(decision.reason.contains("DETERMINISTIC_VERIFICATION"))
        assertTrue(decision.reason.contains("COMPLETION_GATE"))
    }

    private fun report(metricPassed: Boolean) = EvaluationReport(
        subjectId = "subject",
        metrics = listOf(
            EvaluationMetric(
                kind = EvaluationMetricKind.ARTIFACT_READY,
                passed = metricPassed,
                severity = EvaluationSeverity.BLOCKER,
                evidence = "fixture"
            )
        )
    )
}
