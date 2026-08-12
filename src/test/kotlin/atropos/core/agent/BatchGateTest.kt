package atropos.core.agent

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BatchGateTest {
    private val gate = BatchGate()

    @Test
    fun unchanged_or_declared_changes_have_zero_unauthorized_delta() {
        val result = gate.evaluate(
            before = mapOf("src/A.kt" to "one", "src/B.kt" to "two"),
            after = mapOf("src/A.kt" to "changed", "src/B.kt" to "two"),
            declaredTerritory = setOf("src")
        )

        assertTrue(result.passed, result.reason)
        assertTrue(result.delta.isZero)
        assertTrue(result.delta.changedPaths == setOf("src/A.kt"))
    }

    @Test
    fun changes_outside_declared_territory_fail_closed() {
        val result = gate.evaluate(
            before = mapOf("src/A.kt" to "one"),
            after = mapOf("src/A.kt" to "changed", "README.md" to "unexpected"),
            declaredTerritory = setOf("src")
        )

        assertFalse(result.passed)
        assertFalse(result.delta.isZero)
        assertTrue(result.reason.contains("README.md"))
    }
}
