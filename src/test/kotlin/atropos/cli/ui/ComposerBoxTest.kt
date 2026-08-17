/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.ui

import atropos.cli.config.ConfigurationManager
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The composer has to look like a place you type, not like more transcript.
 *
 * Drawn with a left rail alone, the input line was text starting two columns
 * in — which is exactly what every line of output above it also was. An
 * operator could not tell what they had typed from what the engine had said
 * back, on the surface where that distinction matters most.
 */
class ComposerBoxTest {

    private val theme = TerminalTheme(ConfigurationManager(envProvider = { null }, hasConsole = false))

    private fun composer(text: String = "", cursor: Int = text.length): ComposerViewport =
        ComposerViewport(theme).apply { update(text, "", cursor, "ASK") }

    @Test
    fun the_input_is_enclosed_on_all_four_sides() {
        val viewport = composer("build the DAG")
        val body = viewport.renderMultiline(40, 3).lines.map(TerminalText::stripAnsi)
        val bottom = TerminalText.stripAnsi(viewport.metaRow("groq", 40).single())

        assertTrue(body.first().startsWith("╭"), "no top-left corner: ${body.first()}")
        assertTrue(body.first().endsWith("╮"), "no top-right corner: ${body.first()}")
        body.drop(1).forEach { line ->
            assertTrue(line.startsWith("│") && line.endsWith("│"), "input row is not enclosed: $line")
        }
        assertTrue(bottom.startsWith("╰") && bottom.endsWith("╯"), "no bottom edge: $bottom")
    }

    @Test
    fun every_edge_is_exactly_the_terminal_width() {
        val viewport = composer("a somewhat longer line of input text")
        val width = 52

        (viewport.renderMultiline(width, 3).lines + viewport.metaRow("groq", width)).forEach { line ->
            assertEquals(width, TerminalText.cellWidth(line), "edge does not span the terminal: $line")
        }
    }

    @Test
    fun the_caret_sits_inside_the_box_and_below_its_top_edge() {
        val snapshot = composer("hello").renderMultiline(40, 3)

        // Row 0 is the top border, so the first line of input is row 1.
        assertTrue(snapshot.cursorRow >= 1, "the caret was placed on the box's own top edge")
        // `│ hello` — the border, one pad, then five characters typed.
        assertEquals(8, snapshot.cursorColumn)
    }

    @Test
    fun the_mode_and_provider_label_the_box_rather_than_the_input_line() {
        val bottom = TerminalText.stripAnsi(composer().metaRow("groq", 44).single())

        assertTrue(bottom.contains("ask"), "the mode is not shown: $bottom")
        assertTrue(bottom.contains("groq"), "the provider is not shown: $bottom")
    }

    @Test
    fun typed_text_is_never_cut_to_fit_the_border() {
        val text = "the quick brown fox jumps over the lazy dog"
        val snapshot = composer(text).renderMultiline(30, 4)
        val typed = snapshot.lines.drop(1)
            .joinToString("") { TerminalText.stripAnsi(it).removePrefix("│").removeSuffix("│").trim() + " " }

        assertTrue(
            text.split(" ").all { word -> word in typed },
            "the box ate part of the input: '$typed'"
        )
    }
}
