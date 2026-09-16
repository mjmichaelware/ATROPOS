/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class EvidenceMorphTest {
    @Test
    fun evidence_expands_in_place_only_when_present() {
        val morph = EvidenceMorph()
        val collapsed = morph.morph("answer", null, expanded = true, width = 80)
        assertEquals(EvidenceMorph.Surface.CARD, collapsed.surface)
        assertTrue(!collapsed.expanded)

        val expanded = morph.morph("answer", "hash=abc", expanded = true, width = 80)
        assertEquals(EvidenceMorph.Surface.DRAWER, expanded.surface)
        assertTrue(expanded.text.contains("hash=abc"))
        assertTrue(expanded.expanded)
    }

    @Test
    fun collapsed_when_not_expanded() {
        val morph = EvidenceMorph()
        val collapsed = morph.morph("answer", "hash=abc", expanded = false, width = 80)

        assertEquals(EvidenceMorph.Surface.CARD, collapsed.surface)
        assertFalse(collapsed.expanded)
        assertEquals("answer", collapsed.text)
    }

    @Test
    fun ellipsis_truncates_long_text() {
        val morph = EvidenceMorph()
        val longSummary = "a".repeat(200)
        val collapsed = morph.morph(longSummary, null, expanded = false, width = 80)

        assertTrue(collapsed.text.length <= 80)
        assertTrue(collapsed.text.endsWith("…"))
    }

    @Test
    fun expanded_includes_evidence() {
        val morph = EvidenceMorph()
        val expanded = morph.morph("summary text", "sha256:abc123...", expanded = true, width = 120)

        assertEquals(EvidenceMorph.Surface.DRAWER, expanded.surface)
        assertTrue(expanded.text.contains("summary text"))
        assertTrue(expanded.text.contains("sha256"))
        assertTrue(expanded.expanded)
    }

    @Test
    fun expanded_without_evidence_stays_card() {
        val morph = EvidenceMorph()
        val expanded = morph.morph("summary text", null, expanded = true, width = 80)

        assertEquals(EvidenceMorph.Surface.CARD, expanded.surface)
        assertFalse(expanded.expanded)
    }
}