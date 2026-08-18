/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.ui

import atropos.cli.config.ConfigurationManager
import atropos.cli.session.QuotaSessionTracker
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The screen, checked for the things a screenshot showed and code review did not.
 */
class ScreenFurnitureTest {

    private val theme = TerminalTheme(ConfigurationManager(envProvider = { null }, hasConsole = false))
    private val layout = ViewportLayout(theme, WelcomePanel(theme), StatusBarRenderer(theme))

    private fun tab(name: String, active: Boolean) =
        ViewportLayout.TabState(name, name, active, ViewportLayout.TrustIndicator.UNKNOWN)

    private fun frame(width: Int = 80, height: Int = 30, tabs: List<ViewportLayout.TabState> = listOf(tab("Dashboard", true))) =
        layout.build(
            width = width, height = height, transcript = TranscriptBuffer(),
            composer = ComposerViewport(theme), provider = "groq", workspace = "/w",
            tracker = QuotaSessionTracker(), activity = null, verificationState = null, tabs = tabs
        )

    @Test
    fun the_caret_sits_inside_the_composer_and_not_on_its_border() {
        // The canvas emits `ESC[row;colH`, which counts from one, while the
        // layout counts rows from zero. The missing +1 drew the caret one row
        // high — on the box's own top edge, visibly outside the input.
        val built = frame()
        val rows = built.copyLines().map(TerminalText::stripAnsi)

        val caretRow = built.cursorY - 1
        assertTrue(caretRow in rows.indices, "the caret is off-screen at row ${built.cursorY}")
        assertTrue(
            !rows[caretRow].trimStart().startsWith("╭"),
            "the caret was placed on the composer's top border: '${rows[caretRow]}'"
        )
        assertTrue(
            rows[caretRow].trimStart().startsWith("│"),
            "the caret is not on an input row: '${rows[caretRow]}'"
        )
    }

    @Test
    fun the_active_tab_is_drawn_as_a_tab() {
        val rows = frame().copyLines().map(TerminalText::stripAnsi)

        assertTrue(rows.any { it.contains("╭─") }, "no tab box was drawn:\n${rows.take(4).joinToString("\n")}")
        assertTrue(rows.any { it.contains("Dashboard") }, "the tab has no name")
    }

    @Test
    fun the_tab_name_appears_once_at_the_top() {
        // The chrome printed the tab name in plain text directly above a tab
        // bar showing the same name, so it appeared twice before the content
        // even started.
        val top = frame().copyLines().take(4).map(TerminalText::stripAnsi)
        val mentions = top.count { it.contains("Dashboard") }

        assertEquals(1, mentions, "the tab name appears $mentions times in the chrome:\n${top.joinToString("\n")}")
    }

    @Test
    fun the_provider_is_named_once_on_the_screen() {
        // It was in the composer's bottom border and in the footer, and
        // neither copy said which one was authoritative.
        val rows = frame().copyLines().map(TerminalText::stripAnsi)
        val mentions = rows.count { it.contains("groq") }

        assertEquals(1, mentions, "the provider appears $mentions times:\n${rows.filter { it.contains("groq") }.joinToString("\n")}")
    }

    @Test
    fun the_empty_home_screen_has_no_black_band_below_its_content() {
        val rows = frame(height = 34).copyLines().map(TerminalText::stripAnsi)
        val blankTail = rows.dropLast(4).takeLastWhile(String::isBlank).size

        assertTrue(blankTail <= 2, "the home screen ends in $blankTail blank rows")
    }

    @Test
    fun no_row_ever_overflows_the_terminal() {
        listOf(40, 46, 80, 120).forEach { width ->
            frame(width = width).copyLines().forEach { line ->
                assertTrue(
                    TerminalText.cellWidth(line) <= width,
                    "a row overflowed $width columns: ${TerminalText.cellWidth(line)}"
                )
            }
        }
    }
}
