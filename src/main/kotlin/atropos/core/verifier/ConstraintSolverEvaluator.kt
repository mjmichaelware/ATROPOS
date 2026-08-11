package atropos.core.verifier

import atropos.core.verification.DeterministicClassification
import atropos.core.verification.DeterministicFinding
import atropos.core.verification.DiagnosticSeverity

enum class BoundaryRule {
    PATH_WITHIN_ROOT,
    PATH_UNDER_SRC,
    PATH_IS_REGULAR_FILE
}

data class DeterministicConstraint(
    val invariantId: String,
    val satisfied: Boolean,
    val expected: String,
    val observed: String,
    val remediation: String,
    val severity: DiagnosticSeverity = DiagnosticSeverity.ERROR,
    val file: String? = null,
    val symbolOrLocation: String? = null,
    val classification: DeterministicClassification = DeterministicClassification.DETERMINISTIC
)

data class BoundaryConstraint(
    val invariantId: String,
    val rule: BoundaryRule,
    val expected: String,
    val observed: String,
    val remediation: String,
    val severity: DiagnosticSeverity = DiagnosticSeverity.ERROR,
    val file: String? = null,
    val symbolOrLocation: String? = null,
    val classification: DeterministicClassification = DeterministicClassification.DETERMINISTIC
)

class ConstraintSolverEvaluator {
    fun evaluate(vararg constraints: DeterministicConstraint): List<DeterministicFinding> =
        evaluate(constraints.asList())

    fun evaluate(constraints: List<DeterministicConstraint>): List<DeterministicFinding> =
        constraints.filterNot { it.satisfied }.map(::toFinding)

    fun evaluateBoundaries(vararg constraints: BoundaryConstraint): List<DeterministicFinding> =
        evaluateBoundaries(constraints.asList())

    fun evaluateBoundaries(constraints: List<BoundaryConstraint>): List<DeterministicFinding> =
        constraints.map(::toBoundaryFinding)

    private fun toFinding(constraint: DeterministicConstraint): DeterministicFinding =
        DeterministicFinding(
            invariantId = constraint.invariantId,
            severity = constraint.severity,
            file = constraint.file,
            symbolOrLocation = constraint.symbolOrLocation,
            evidence = "expected=${constraint.expected}; observed=${constraint.observed}",
            remediation = constraint.remediation,
            classification = constraint.classification
        )

    private fun toBoundaryFinding(constraint: BoundaryConstraint): DeterministicFinding =
        DeterministicFinding(
            invariantId = constraint.invariantId,
            severity = constraint.severity,
            file = constraint.file,
            symbolOrLocation = constraint.symbolOrLocation,
            evidence = "rule=${constraint.rule.name}; expected=${constraint.expected}; observed=${constraint.observed}",
            remediation = constraint.remediation,
            classification = constraint.classification
        )
}
