package atropos.core.evaluation

import java.time.Instant
import java.util.UUID

enum class EvaluationMetricKind {
    ARTIFACT_READY,
    VERIFICATION_PASSED,
    INSTALL_OR_RUN_PROOF,
    JOURNAL_EVIDENCE,
    MEMORY_EVIDENCE,
    POLICY_EVIDENCE,
    PROMOTION_SCOPE_EVIDENCE,
    TERRITORY_EVIDENCE,
    AUDITOR_PROMOTION_GATE,
    DIRECTOR_PROMOTION_ADVISORY,
    SECRET_SAFETY,
    SELF_APPROVAL_GUARD,
    FAKE_SUCCESS_GUARD,
    MYTHOLOGY_GUARD,
    REPRODUCIBILITY
}

enum class EvaluationSeverity {
    INFO,
    WARNING,
    BLOCKER
}

data class EvaluationMetric(
    val kind: EvaluationMetricKind,
    val passed: Boolean,
    val severity: EvaluationSeverity,
    val evidence: String
)

data class EvaluationReport(
    val id: String = "eval-${UUID.randomUUID().toString().take(12)}",
    val subjectId: String,
    val runId: String? = null,
    val artifactIds: List<String> = emptyList(),
    val metrics: List<EvaluationMetric>,
    val createdAt: Instant = Instant.now()
) {
    val passed: Boolean get() = metrics.none { !it.passed && it.severity == EvaluationSeverity.BLOCKER }
    val blockerCount: Int get() = metrics.count { !it.passed && it.severity == EvaluationSeverity.BLOCKER }
    val summary: String get() = "evaluation $id subject=$subjectId passed=$passed blockers=$blockerCount metrics=${metrics.size}"
}

data class ReleaseGateDecision(
    val accepted: Boolean,
    val report: EvaluationReport,
    val reason: String
)
