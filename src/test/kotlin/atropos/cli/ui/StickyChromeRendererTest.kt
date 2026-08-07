/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.ui

import atropos.cli.config.ConfigurationManager
import kotlin.test.Test
import kotlin.test.assertEquals

class StickyChromeRendererTest {
    private val renderer = StickyChromeRenderer(TerminalTheme(ConfigurationManager()))

    @Test
    fun renderCompactMode() {
        val lines = renderer.render("MyProject", 2, 60, isDensity = true)
        assertEquals(1, lines.size, "Compact mode should be single line")
        assertEquals(true, lines[0].contains("MyProject"), "Should include project name")
        assertEquals(true, lines[0].contains("2 tabs"), "Should show tab count")
    }

    @Test
    fun renderComfortableMode() {
        val lines = renderer.render("MyProject", 1, 60, isDensity = false)
        assertEquals(2, lines.size, "Comfortable mode should be two lines")
        assertEquals(true, lines[0].contains("▌"), "First line should have marker")
        assertEquals(true, lines[1].contains("[1 tab]"), "Second line should show count")
    }

    @Test
    fun handleNarrowTerminal() {
        val lines = renderer.render("VeryLongProjectNameThatExceedsWidth", 5, 30, isDensity = false)
        assertEquals(true, lines[0].length <= 30, "Line should not exceed width")
    }
}
