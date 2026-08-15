// SPDX-License-Identifier: AGPL-3.0-only
package atropos.core.verification

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.Test

class AssertionNamingTest {
    @Test
    fun `require passes when condition is true`() {
        // Should not throw
        NamedAssertion.require(true, "AlwaysTrue", "value")
    }

    @Test
    fun `require throws structured message when condition is false`() {
        val exception = assertThrows<IllegalArgumentException> {
            NamedAssertion.require(false, "MustBePositive", -5)
        }
        assertEquals("Invariant failed: [MustBePositive]. Observed: -5", exception.message)
    }
}
