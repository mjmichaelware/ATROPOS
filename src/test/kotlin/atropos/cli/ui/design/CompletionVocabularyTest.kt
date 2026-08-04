/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.ui.design

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CompletionVocabularyTest {

    @Test
    fun `all five P20-G09 states exist and stay distinct`() {
        assertEquals(
            listOf("implemented", "compiled", "tested", "verified", "blocked"),
            CompletionState.ORDER.map { it.canonical }
        )
        assertEquals(
            CompletionState.ORDER.size,
            CompletionState.ORDER.map { it.canonical }.toSet().size,
            "two states sharing a term is the collapse P20-G09 forbids"
        )
    }

    @Test
    fun `only verified is a positive completion claim`() {
        assertTrue(CompletionState.VERIFIED.isPositiveClaim)
        CompletionState.entries.filter { it != CompletionState.VERIFIED }.forEach {
            assertFalse(it.isPositiveClaim, "${it.canonical} must not read as completion")
        }
    }

    @Test
    fun `tested is not verified — self-run tests are not independent agreement`() {
        assertFalse(
            CompletionState.TESTED.isPositiveClaim,
            "a component that passed its own tests has self-approved"
        )
    }

    @Test
    fun `infer fails closed on an unobserved step`() {
        assertEquals(CompletionState.IMPLEMENTED, CompletionState.infer(null, null, null))
        assertEquals(CompletionState.COMPILED, CompletionState.infer(true, null, null))
        assertEquals(CompletionState.TESTED, CompletionState.infer(true, true, null))
        assertEquals(CompletionState.VERIFIED, CompletionState.infer(true, true, true))
    }

    @Test
    fun `a failed step blocks regardless of what else passed`() {
        assertEquals(CompletionState.BLOCKED, CompletionState.infer(false, true, true))
        assertEquals(CompletionState.BLOCKED, CompletionState.infer(true, false, true))
        assertEquals(CompletionState.BLOCKED, CompletionState.infer(true, true, false))
        assertEquals(
            CompletionState.BLOCKED,
            CompletionState.infer(true, true, true, blocked = true),
            "an explicit block outranks every passing observation"
        )
    }

    @Test
    fun `every state carries a non-colour signal`() {
        CompletionState.entries.forEach {
            assertTrue(it.signal.isNotBlank(), "${it.canonical} has no non-colour channel")
            assertTrue(it.meaning.isNotBlank())
        }
    }

    @Test
    fun `canonical terms resolve and unknown terms refuse to guess`() {
        assertEquals(CompletionState.VERIFIED, CompletionState.fromCanonical("verified"))
        assertEquals(CompletionState.VERIFIED, CompletionState.fromCanonical(" VERIFIED "))
        assertNull(CompletionState.fromCanonical("done"))
        assertNull(CompletionState.fromCanonical("ok"))
    }
}
