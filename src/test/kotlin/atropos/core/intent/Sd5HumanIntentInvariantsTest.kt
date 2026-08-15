/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.intent

import kotlin.test.*

class Sd5HumanIntentInvariantsTest {

    @Test
    fun `test all 48 invariants are generated`() {
        assertEquals(48, Sd5HumanIntentInvariants.INVARIANTS.size)
    }

    @Test
    fun `test invariants are correctly categorized`() {
        val humanIntent = Sd5HumanIntentInvariants.INVARIANTS.filter { it.category == InvariantCategory.HUMAN_INTENT }
        val authority = Sd5HumanIntentInvariants.INVARIANTS.filter { it.category == InvariantCategory.AUTHORITY_CASCADE }
        val system = Sd5HumanIntentInvariants.INVARIANTS.filter { it.category == InvariantCategory.SYSTEM_INVARIANT }

        assertEquals(16, humanIntent.size)
        assertEquals(16, authority.size)
        assertEquals(16, system.size)

        assertEquals("INV-001", humanIntent.first().id)
        assertEquals("INV-017", authority.first().id)
        assertEquals("INV-033", system.first().id)
    }

    @Test
    fun `test invariant lookup`() {
        val inv = Sd5HumanIntentInvariants.getInvariant("INV-048")
        assertNotNull(inv)
        assertEquals(InvariantCategory.SYSTEM_INVARIANT, inv?.category)
        assertTrue(inv!!.description.contains("INV-048"))
    }

    @Test
    fun `test validation passes`() {
        assertTrue(Sd5HumanIntentInvariants.validateAll())
    }
}
