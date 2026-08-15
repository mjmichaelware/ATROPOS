// SPDX-License-Identifier: AGPL-3.0-only
package atropos.core.verification

import kotlin.test.*

class AssertionNamingTest {
    @Test
    fun `require passes when condition is true`() {
        // Should not throw
        NamedAssertion.require(true, "AlwaysTrue", "value")
    }

    @Test
    fun `require throws structured message when condition is false`() {
        val exception = assertFailsWith<IllegalArgumentException> {
            NamedAssertion.require(false, "MustBePositive", -5)
        }
        assertEquals("Invariant failed: [MustBePositive]. Observed: -5", exception.message)
    }
}
