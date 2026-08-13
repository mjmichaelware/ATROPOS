/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.ui

import atropos.cli.config.ConfigurationManager
import atropos.cli.ui.design.ColorTier
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A phone in portrait is the narrowest surface this product runs on, and it is
 * the one the operator actually watches a long run from. Anything the formatter
 * discards to make a line fit is discarded there first.
 */
class RailBlockFormatterTest {

    private val theme = TerminalTheme(
        ConfigurationManager(
            envProvider = { name -> if (name == "TERM") "xterm-256color" else null },
            hasConsole = true
        ),
        tierOverride = ColorTier.TRUECOLOR
    )

    private fun visible(value: String): String = TerminalText.stripAnsi(value)

    /**
     * The regression that motivated wrapping. A lakehouse trace ends in
     * `reason=…`, which is the only part that says what to do next; ellipsizing
     * at phone width cut exactly that off.
     */
    @Test
    fun `a long value survives a narrow viewport instead of being cut`() {
        val line = "  lakehouse: path=N/build/build_systems status=MISS reason=no_object_for_path"

        val formatted = RailBlockFormatter.format(line, theme, width = 40)

        assertTrue(formatted.contains("\n"), "a line too long to fit must wrap, not shrink")
        assertTrue(
            visible(formatted).contains("no_object_for_path"),
            "the reason must survive; it is the part that says what to do next"
        )
    }

    @Test
    fun `no rendered line exceeds the viewport`() {
        val line = "  evidence: /data/data/com.termux/files/home/ATROPOS/.atropos/runs/factory-8f5f104fd5cdfaf0/events.journal"

        RailBlockFormatter.format(line, theme, width = 48).lines().forEach { rendered ->
            assertTrue(
                TerminalText.cellWidth(rendered) <= 48,
                "line overflowed the viewport: ${TerminalText.cellWidth(rendered)} cells"
            )
        }
    }

    /**
     * [TerminalText.clip] returns ANSI-stripped text, so the old path lost all
     * colour on precisely the dense lines that most needed it — while short
     * lines beside them kept theirs.
     */
    @Test
    fun `a wrapped value keeps the colour it was given`() {
        val painted = theme.success("PASS") + " " + "x".repeat(120)

        val formatted = RailBlockFormatter.format("  status: $painted", theme, width = 40)

        assertTrue(formatted.contains("["), "wrapping must not strip the palette")
        assertTrue(visible(formatted).contains("PASS"))
    }

    @Test
    fun `a value that already fits is left on one line`() {
        val formatted = RailBlockFormatter.format("  status: PASS", theme, width = 80)

        assertEquals(1, formatted.lines().size)
    }

    @Test
    fun `continuation lines hang under the value column`() {
        val formatted = RailBlockFormatter.format("  reason: " + "word ".repeat(40), theme, width = 40)
        val rendered = formatted.lines()

        assertTrue(rendered.size > 1, "this value cannot fit on one line")
        // Measured after the rail, which every line carries.
        val body = visible(rendered[1]).dropWhile { it != ' ' }
        val indent = body.takeWhile { it == ' ' }.length
        assertTrue(indent > 1, "a continuation must not start in the label column")
    }

    @Test
    fun `blank input is passed through untouched`() {
        assertEquals("   ", RailBlockFormatter.format("   ", theme, width = 40))
    }
}
