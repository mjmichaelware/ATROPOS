/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.ui

import atropos.cli.config.ConfigurationManager
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SessionTabBarTest {
    private val tabBar = SessionTabBar(TerminalTheme(ConfigurationManager()))

    @Test
    fun renderEmptyTabs() {
        val lines = tabBar.render(emptyList(), 60)
        assertEquals(0, lines.size, "Empty tab list should render no lines")
    }

    @Test
    fun renderSingleTab() {
        val tabs = listOf(
            ViewportLayout.TabState(
                id = "tab1",
                name = "Session 1",
                isActive = true,
                trustLevel = ViewportLayout.TrustIndicator.ATTESTED
            )
        )
        // Two rows now, not one. A single inverted row was a highlighted
        // word; a tab needs a box, and the box needs a top.
        val lines = tabBar.render(tabs, 60)
        assertEquals(2, lines.size, "Should render a tab box: a top edge and a label row")
        assertTrue(TerminalText.stripAnsi(lines[0]).contains("╭"), "Should draw the tab's top edge")
        assertTrue(TerminalText.stripAnsi(lines[1]).contains("●"), "Should show attested indicator")
        assertTrue(TerminalText.stripAnsi(lines[1]).contains("Session 1"), "Should show tab name")
    }

    @Test
    fun renderMultipleTabs() {
        val tabs = listOf(
            ViewportLayout.TabState("tab1", "Session 1", true, ViewportLayout.TrustIndicator.ATTESTED),
            ViewportLayout.TabState("tab2", "Session 2", false, ViewportLayout.TrustIndicator.UNATTESTED),
            ViewportLayout.TabState("tab3", "Session 3", false, ViewportLayout.TrustIndicator.UNKNOWN)
        )
        val lines = tabBar.render(tabs, 80)
        assertEquals(2, lines.size)
        val labels = TerminalText.stripAnsi(lines[1])
        assertTrue(labels.contains("●"), "Should show attested indicator")
        assertTrue(labels.contains("○"), "Should show unattested indicator")
        // `·` rather than `?`: a question mark in a tab reads as a broken
        // glyph, where a dot reads as "nothing has been checked here".
        assertTrue(labels.contains("·"), "Should show unknown indicator")
    }

    @Test
    fun respectNarrowWidth() {
        val tabs = listOf(
            ViewportLayout.TabState("tab1", "VeryLongSessionName", true, ViewportLayout.TrustIndicator.ATTESTED)
        )
        val lines = tabBar.render(tabs, 35)
        assertEquals(2, lines.size)
        lines.forEach { line ->
            assertTrue(TerminalText.cellWidth(line) <= 35, "Should not exceed width")
        }
    }
}
