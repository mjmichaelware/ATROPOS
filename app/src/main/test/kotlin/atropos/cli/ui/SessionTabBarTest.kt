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
        val lines = tabBar.render(tabs, 60)
        assertEquals(1, lines.size, "Should render one line")
        assertTrue(lines[0].contains("●"), "Should show attested indicator")
        assertTrue(lines[0].contains("Session 1"), "Should show tab name")
    }

    @Test
    fun renderMultipleTabs() {
        val tabs = listOf(
            ViewportLayout.TabState("tab1", "Session 1", true, ViewportLayout.TrustIndicator.ATTESTED),
            ViewportLayout.TabState("tab2", "Session 2", false, ViewportLayout.TrustIndicator.UNATTESTED),
            ViewportLayout.TabState("tab3", "Session 3", false, ViewportLayout.TrustIndicator.UNKNOWN)
        )
        val lines = tabBar.render(tabs, 80)
        assertEquals(1, lines.size)
        assertTrue(lines[0].contains("●"), "Should show attested indicator")
        assertTrue(lines[0].contains("○"), "Should show unattested indicator")
        assertTrue(lines[0].contains("?"), "Should show unknown indicator")
    }

    @Test
    fun respectNarrowWidth() {
        val tabs = listOf(
            ViewportLayout.TabState("tab1", "VeryLongSessionName", true, ViewportLayout.TrustIndicator.ATTESTED)
        )
        val lines = tabBar.render(tabs, 35)
        assertEquals(1, lines.size)
        assertTrue(lines[0].length <= 35, "Should not exceed width")
    }
}
