/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.verifier

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

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
}
