/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.ui

import atropos.cli.config.ConfigurationManager
import atropos.cli.session.QuotaSessionTracker
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tabs the operator can see.
 *
 * `ViewportLayout.build` has always taken a tab list and always defaulted it to
 * empty, and no caller ever passed one — so `SessionTabBar.render` returned on
 * its first line every single frame. Tabs existed, `/tabs` switched between
 * them, and none of it appeared on screen.
 */
class TabBarVisibilityTest {

    private val theme = TerminalTheme(ConfigurationManager(envProvider = { null }, hasConsole = false))
    private val layout = ViewportLayout(theme, WelcomePanel(theme), StatusBarRenderer(theme))

    private fun tab(id: String, name: String, active: Boolean) =
        ViewportLayout.TabState(id, name, active, ViewportLayout.TrustIndicator.UNKNOWN)

    private fun frame(tabs: List<ViewportLayout.TabState>): List<String> =
        layout.build(
            width = 80,
            height = 24,
            transcript = TranscriptBuffer(),
            composer = ComposerViewport(theme),
            provider = "groq",
            workspace = "/w",
            tracker = QuotaSessionTracker(),
            activity = null,
            verificationState = null,
            tabs = tabs
        ).copyLines().map(TerminalText::stripAnsi).toList()

    @Test
    fun open_tabs_appear_in_the_frame() {
        val rendered = frame(
            listOf(tab("1", "Dashboard", true), tab("2", "Factory", false))
        ).joinToString("\n")

        assertTrue(rendered.contains("Dashboard"), "the first tab is not on screen:\n$rendered")
        assertTrue(rendered.contains("Factory"), "the second tab is not on screen:\n$rendered")
    }

    @Test
    fun a_single_tab_still_shows_its_name() {
        assertTrue(
            frame(listOf(tab("1", "Chat", true))).any { it.contains("Chat") },
            "one tab was treated as no tabs"
        )
    }

    @Test
    fun no_tabs_costs_no_rows() {
        // The bar must not reserve a blank row when there is nothing to say;
        // a screen this small cannot spare one.
        val without = frame(emptyList())
        val with = frame(listOf(tab("1", "Dashboard", true)))

        assertEquals(without.size, with.size, "the frame changed height")
        assertTrue(
            without.take(4) != with.take(4),
            "adding a tab changed nothing in the chrome"
        )
    }

    @Test
    fun the_bar_never_overflows_the_terminal() {
        val many = (1..12).map { tab(it.toString(), "A rather long tab title $it", it == 1) }

        frame(many).forEach { line ->
            assertTrue(
                TerminalText.cellWidth(line) <= 80,
                "a frame line overflowed 80 columns: ${TerminalText.cellWidth(line)}"
            )
        }
    }

    @Test
    fun trust_is_reported_as_unknown_rather_than_asserted() {
        // §0.6. An indicator that shows every tab as attested because nothing
        // checked is a fake attestation drawn into the chrome.
        val rendered = SessionTabBar(theme)
            .render(listOf(tab("1", "Dashboard", true)), 80)
            .joinToString("\n")
            .let(TerminalText::stripAnsi)

        assertTrue(rendered.contains("?"), "trust was claimed rather than marked unknown: $rendered")
        assertTrue(!rendered.contains("●"), "an unchecked tab was drawn as attested: $rendered")
    }
}
