/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.verifier

import kotlin.test.*
import java.nio.file.Files

class ConstraintSolverEvaluatorExtendedTest {
    @Test
    fun `test regex and numeric constraints`() {
        val evaluator = ConstraintSolverEvaluator()
        
        val constraintRegex = BoundaryConstraint(
            invariantId = "INV-REGEX",
            rule = BoundaryRule.REGEX_MATCH,
            expected = "^[A-Z]+$",
            observed = "HELLO",
            remediation = "Fix regex"
        )
        
        val constraintNum = BoundaryConstraint(
            invariantId = "INV-NUM",
            rule = BoundaryRule.NUMERIC_RANGE,
            expected = "10.0..20.0",
            observed = "15.5",
            remediation = "Fix range"
        )
        
        val findings = evaluator.evaluateBoundaries(constraintRegex, constraintNum)
        assertTrue(findings.isEmpty())
    }

    @Test
    fun `every boundary rule has an evaluated valid path`() {
        val root = Files.createTempDirectory("constraint-rules-")
        val constraints = BoundaryRule.entries.map { rule ->
            val expected: String
            val observed: String
            when (rule) {
                BoundaryRule.PATH_WITHIN_ROOT -> {
                    expected = root.toString()
                    observed = root.resolve("src/App.kt").toString()
                }
                BoundaryRule.EXACT_VALUE -> { expected = "same"; observed = "same" }
                BoundaryRule.NON_EMPTY -> { expected = "any"; observed = "present" }
                BoundaryRule.NO_FORBIDDEN_TOKEN -> { expected = "secret,token"; observed = "safe value" }
                BoundaryRule.REGEX_MATCH -> { expected = "^[A-Z]+$"; observed = "VALID" }
                BoundaryRule.NUMERIC_RANGE -> { expected = "1..3"; observed = "2" }
            }
            BoundaryConstraint(
                invariantId = "rule-${rule.name}",
                rule = rule,
                expected = expected,
                observed = observed,
                remediation = "repair ${rule.name}"
            )
        }

        assertTrue(ConstraintSolverEvaluator().evaluateBoundaries(*constraints.toTypedArray()).isEmpty())
    }

    @Test
    fun `every boundary rule rejects its invalid path`() {
        val root = Files.createTempDirectory("constraint-invalid-rules-")
        val constraints = BoundaryRule.entries.map { rule ->
            when (rule) {
                BoundaryRule.PATH_WITHIN_ROOT -> BoundaryConstraint(
                    "invalid-${rule.name}", rule, root.toString(), "/outside/App.kt", "repair path"
                )
                BoundaryRule.EXACT_VALUE -> BoundaryConstraint(
                    "invalid-${rule.name}", rule, "expected", "observed", "repair exact value"
                )
                BoundaryRule.NON_EMPTY -> BoundaryConstraint(
                    "invalid-${rule.name}", rule, "any", "", "repair empty value"
                )
                BoundaryRule.NO_FORBIDDEN_TOKEN -> BoundaryConstraint(
                    "invalid-${rule.name}", rule, "secret,token", "contains secret", "remove token"
                )
                BoundaryRule.REGEX_MATCH -> BoundaryConstraint(
                    "invalid-${rule.name}", rule, "^[A-Z]+$", "lowercase", "repair regex"
                )
                BoundaryRule.NUMERIC_RANGE -> BoundaryConstraint(
                    "invalid-${rule.name}", rule, "1..3", "9", "repair range"
                )
            }
        }

        val findings = ConstraintSolverEvaluator().evaluateBoundaries(*constraints.toTypedArray())
        assertEquals(BoundaryRule.entries.size, findings.size)
        assertEquals(BoundaryRule.entries.map { "invalid-${it.name}" }.toSet(), findings.map { it.invariantId }.toSet())
    }
}
