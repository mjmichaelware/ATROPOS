package atropos.core.evaluation

import atropos.core.verification.CompletionGateReport
import atropos.core.verification.DeterministicVerificationResult

/** Aggregates existing gate outcomes into one fail-closed release decision. */
class ReleaseGateEvaluator {
    fun evaluate(
        report: EvaluationReport,
        deterministic: DeterministicVerificationResult? = null,
        completion: CompletionGateReport? = null
    ): ReleaseGateDecision {
        val additional = buildList {
            deterministic?.let {
                add(
                    EvaluationMetric(
                        kind = EvaluationMetricKind.DETERMINISTIC_VERIFICATION,
                        passed = it.passed,
                        severity = EvaluationSeverity.BLOCKER,
                        evidence = "findings=${it.findings.size}"
                    )
                )
            }
            completion?.let {
                add(
                    EvaluationMetric(
                        kind = EvaluationMetricKind.COMPLETION_GATE,
                        passed = it.canComplete,
                        severity = EvaluationSeverity.BLOCKER,
                        evidence = it.message
                    )
                )
            }
        }
        val evaluated = if (additional.isEmpty()) report else report.copy(metrics = report.metrics + additional)
        val blockers = evaluated.metrics.filter { !it.passed && it.severity == EvaluationSeverity.BLOCKER }
        return ReleaseGateDecision(
            accepted = blockers.isEmpty(),
            report = evaluated,
            reason = if (blockers.isEmpty()) {
                "release gate passed"
            } else {
                blockers.joinToString("; ") { "${it.kind}: ${it.evidence}" }
            }
        )
    }
}
