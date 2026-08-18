/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.ui

import atropos.cli.config.ConfigurationManager
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The home screen has to earn the rows it occupies.
 *
 * It used to draw a wordmark and one tip and leave two thirds of the display
 * black. Minimal is right while you are working — this panel renders only
 * while the transcript is empty, so the first message replaces it — but an
 * empty screen on first launch teaches nothing.
 */
class IdleHomeScreenTest {

    private val theme = TerminalTheme(ConfigurationManager(envProvider = { null }, hasConsole = false))
    private val renderer = LandingRenderer(theme)

    private fun state() = SessionPresentationState(
        provider = "groq",
        mode = "ASK",
        workspace = "/home/operator/ATROPOS",
        commands = listOf("/help"),
        tokens = MetricValue.Unknown,
        cost = MetricValue.Unknown,
        activeOperation = null,
        activeScreen = "Dashboard",
        activeTab = "tab 1",
        openTabCount = 1,
        activePatchId = null
    )

    private fun text(width: Int, height: Int) =
        renderer.render(state(), width, height).joinToString("\n", transform = TerminalText::stripAnsi)

    @Test
    fun a_full_height_screen_is_not_mostly_empty() {
        val lines = renderer.render(state(), 72, 30).map(TerminalText::stripAnsi)
        val used = lines.count { it.isNotBlank() }

        assertTrue(used >= 12, "the home screen filled only $used of 30 rows")
    }

    @Test
    fun it_says_where_you_are() {
        val rendered = text(72, 30)

        // The provider is stated once, on the composer's border, not here.
        assertTrue(rendered.contains("ATROPOS"), "the workspace is not shown:\n$rendered")
        assertTrue(rendered.contains("workspace"), "the session block is missing:\n$rendered")
    }

    @Test
    fun it_offers_somewhere_to_start() {
        val rendered = text(72, 30)

        assertTrue(rendered.contains("/factory run"), "no starting point is offered:\n$rendered")
        assertTrue(rendered.contains("@path"), "attaching a file is undiscoverable:\n$rendered")
        assertTrue(rendered.contains("/shortcuts"), "the keyboard is undiscoverable:\n$rendered")
    }

    @Test
    fun nothing_on_it_claims_a_state_that_was_never_checked() {
        // §0.6, on the first screen an operator ever sees.
        val rendered = text(72, 30).lowercase()

        listOf("verified", "attested", "healthy", "secure", "all systems").forEach { claim ->
            assertTrue(claim !in rendered, "the home screen claims '$claim'")
        }
    }

    @Test
    fun a_short_terminal_keeps_the_wordmark_rather_than_the_detail() {
        val short = renderer.render(state(), 72, 12).map(TerminalText::stripAnsi)

        assertTrue(short.size <= 12, "the panel returned ${short.size} rows for a 12-row viewport")
        assertTrue(
            short.none { it.contains("START HERE") },
            "detail was drawn into a viewport too short to hold it"
        )
    }

    @Test
    fun no_row_overflows_a_phone_width_terminal() {
        renderer.render(state(), 46, 30).forEach { line ->
            assertTrue(
                TerminalText.cellWidth(line) <= 46,
                "a home row overflowed 46 columns: ${TerminalText.cellWidth(line)}"
            )
        }
    }
}
