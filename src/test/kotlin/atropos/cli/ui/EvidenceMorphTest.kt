package atropos.cli.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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
}
