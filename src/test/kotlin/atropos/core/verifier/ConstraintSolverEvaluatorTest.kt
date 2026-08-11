package atropos.core.verifier

import atropos.core.verification.DeterministicClassification
import atropos.core.verification.DiagnosticSeverity
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ConstraintSolverEvaluatorTest {
    private val evaluator = ConstraintSolverEvaluator()

    @Test
    fun evaluate_returns_findings_only_for_failed_constraints() {
        val findings = evaluator.evaluate(
            DeterministicConstraint(
                invariantId = "package_path_invariant",
                satisfied = false,
                expected = "atropos/core/Foo.kt",
                observed = "src/main/kotlin/example/Foo.kt",
                remediation = "align Kotlin package with file path",
                file = "src/main/kotlin/example/Foo.kt",
                symbolOrLocation = "example",
                severity = DiagnosticSeverity.ERROR
            ),
            DeterministicConstraint(
                invariantId = "redaction",
                satisfied = true,
                expected = "no raw secrets",
                observed = "<redacted>",
                remediation = "none"
            )
        )

        assertEquals(1, findings.size)
        assertEquals("package_path_invariant", findings.single().invariantId)
        assertTrue(findings.single().evidence.contains("expected=atropos/core/Foo.kt"))
        assertTrue(findings.single().evidence.contains("observed=src/main/kotlin/example/Foo.kt"))
        assertEquals(DeterministicClassification.DETERMINISTIC, findings.single().classification)
    }

    @Test
    fun relative_paths_are_resolved_against_the_declared_root() {
        val root = Files.createTempDirectory("atropos-constraint-root-")
        val findings = evaluator.evaluateBoundaries(
            BoundaryConstraint(
                invariantId = "generated_path",
                rule = BoundaryRule.PATH_WITHIN_ROOT,
                expected = root.toString(),
                observed = "src/main/kotlin/App.kt",
                remediation = "keep generated paths inside the project root"
            )
        )

        assertTrue(findings.isEmpty(), findings.toString())
    }
}
