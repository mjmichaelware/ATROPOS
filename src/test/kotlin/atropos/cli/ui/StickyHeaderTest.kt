package atropos.cli.ui

import atropos.cli.config.ConfigurationManager
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StickyHeaderTest {
    @Test
    fun header_height_and_content_are_stable_for_same_viewport() {
        val header = StickyHeader(TerminalTheme(ConfigurationManager()))

        val first = header.render("calculator", 2, 80, isDensity = false)
        val second = header.render("calculator", 2, 80, isDensity = false)

        assertEquals(first, second)
        assertEquals(2, first.height)
        assertTrue(first.lines.all { it.length <= 80 })
    }

    @Test
    fun dense_header_collapses_to_one_pinned_line() {
        val frame = StickyHeader(TerminalTheme(ConfigurationManager())).render("calculator", 1, 40, isDensity = true)

        assertEquals(1, frame.height)
        assertTrue(frame.lines.single().startsWith("▌ "))
    }
}
