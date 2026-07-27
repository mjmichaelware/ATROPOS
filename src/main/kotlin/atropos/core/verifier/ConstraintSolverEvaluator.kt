package atropos.core.verifier

import atropos.core.verification.DeterministicClassification
import atropos.core.verification.DeterministicFinding
import atropos.core.verification.DiagnosticSeverity

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

class ConstraintSolverEvaluator {
    fun evaluate(vararg constraints: DeterministicConstraint): List<DeterministicFinding> =
        evaluate(constraints.asList())

    fun evaluate(constraints: List<DeterministicConstraint>): List<DeterministicFinding> =
        constraints.filterNot { it.satisfied }.map(::toFinding)

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
}
