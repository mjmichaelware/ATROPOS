package atropos.cli.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AttestationOpticalFocusTest {
    @Test
    fun focus_is_visible_without_stealing_input() {
        val focus = AttestationOpticalFocus()
        val cue = focus.cue(attested = true)
        assertEquals("◎", cue.glyph)
        assertEquals("attested", cue.state)
        assertTrue(cue.preservesInput)
        assertTrue(focus.prefix(true).contains("◎"))
    }
}
