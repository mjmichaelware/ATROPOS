package atropos.core.verifier

import atropos.core.verification.DeterministicClassification
import atropos.core.verification.DeterministicFinding
import atropos.core.verification.DiagnosticSeverity
import java.nio.file.Path

enum class BoundaryRule {
    PATH_WITHIN_ROOT,
    EXACT_VALUE,
    NON_EMPTY,
    NO_FORBIDDEN_TOKEN
}

data class BoundaryConstraint(
    val invariantId: String,
    val rule: BoundaryRule,
    val expected: String,
    val observed: String,
    val remediation: String,
    val file: String? = null,
    val symbolOrLocation: String? = null,
    val severity: DiagnosticSeverity = DiagnosticSeverity.ERROR
)

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
        constraints.flatMap { constraint ->
            val invalid = invalidFields(constraint)
            if (invalid.isNotEmpty()) {
                listOf(
                    DeterministicFinding(
                        invariantId = constraint.invariantId.ifBlank { "constraint_schema" },
                        severity = DiagnosticSeverity.ERROR,
                        file = constraint.file,
                        symbolOrLocation = constraint.symbolOrLocation,
                        evidence = "invalid_constraint_fields=${invalid.joinToString(",")}",
                        remediation = "provide a nonblank invariant id, expected value, observed value, and remediation",
                        classification = DeterministicClassification.DETERMINISTIC
                    )
                )
            } else if (!constraint.satisfied) {
                listOf(toFinding(constraint))
            } else {
                emptyList()
            }
        }

    fun evaluateBoundaries(vararg constraints: BoundaryConstraint): List<DeterministicFinding> =
        constraints.mapNotNull { constraint ->
            val invalid = invalidBoundaryFields(constraint)
            if (invalid.isNotEmpty()) {
                return@mapNotNull DeterministicFinding(
                    invariantId = constraint.invariantId.ifBlank { "boundary_schema" },
                    severity = DiagnosticSeverity.ERROR,
                    file = constraint.file,
                    symbolOrLocation = constraint.symbolOrLocation,
                    evidence = "invalid_boundary_fields=${invalid.joinToString(",")}",
                    remediation = "provide a nonblank invariant id, expected value, observed value, and remediation",
                    classification = DeterministicClassification.DETERMINISTIC
                )
            }
            val valid = when (constraint.rule) {
                BoundaryRule.PATH_WITHIN_ROOT -> pathWithinRoot(constraint.observed, constraint.expected)
                BoundaryRule.EXACT_VALUE -> constraint.observed == constraint.expected
                BoundaryRule.NON_EMPTY -> constraint.observed.isNotBlank()
                BoundaryRule.NO_FORBIDDEN_TOKEN -> constraint.expected
                    .split(',')
                    .map(String::trim)
                    .filter(String::isNotBlank)
                    .none { token -> constraint.observed.contains(token) }
            }
            if (valid) null else DeterministicFinding(
                invariantId = constraint.invariantId.ifBlank { "boundary_schema" },
                severity = constraint.severity,
                file = constraint.file,
                symbolOrLocation = constraint.symbolOrLocation,
                evidence = "rule=${constraint.rule.name}; expected=${constraint.expected}; observed=${constraint.observed}",
                remediation = constraint.remediation,
                classification = DeterministicClassification.DETERMINISTIC
            )
        }

    private fun invalidBoundaryFields(constraint: BoundaryConstraint): List<String> = buildList {
        if (constraint.invariantId.isBlank()) add("invariantId")
        if (constraint.expected.isBlank()) add("expected")
        if (constraint.remediation.isBlank()) add("remediation")
        if (constraint.rule == BoundaryRule.NO_FORBIDDEN_TOKEN &&
            constraint.expected.split(',').map(String::trim).none(String::isNotBlank)
        ) add("forbiddenTokens")
    }

    private fun invalidFields(constraint: DeterministicConstraint): List<String> = buildList {
        if (constraint.invariantId.isBlank()) add("invariantId")
        if (constraint.expected.isBlank()) add("expected")
        if (constraint.observed.isBlank()) add("observed")
        if (constraint.remediation.isBlank()) add("remediation")
    }

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

    private fun pathWithinRoot(observed: String, expectedRoot: String): Boolean = runCatching {
        val path = Path.of(observed).toAbsolutePath().normalize()
        val root = Path.of(expectedRoot).toAbsolutePath().normalize()
        path.startsWith(root)
    }.getOrDefault(false)
}
