/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.ui

import atropos.cli.config.ConfigurationManager
import atropos.cli.input.CommandRegistry
import atropos.cli.ui.design.ColorTier
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The home screen on a phone held at a readable font size.
 *
 * A terminal is narrow because the font is large. The layout used to put a
 * label and its meaning side by side, spend the width on the label, and
 * ellipsize the half that carried the meaning -- at thirty columns every row
 * read `/factory run <prompt>  turn…`. The only way to read it was to shrink
 * the font until the text fit, which is the opposite of what the operator
 * wanted, and it is why ATROPOS "looked too small" on a phone.
 */
class NarrowScreenLandingTest {

    private val theme = TerminalTheme(
        ConfigurationManager(envProvider = { null }, hasConsole = false),
        tierOverride = ColorTier.NONE
    )

    private fun state() = SessionPresentationState(
        provider = "groq",
        mode = "ASK",
        workspace = "/home/user/ATROPOS",
        commands = CommandRegistry.quickAccessCommands(),
        tokens = MetricValue.Unknown,
        cost = MetricValue.Unknown,
        activeOperation = null,
        repository = RepositoryState.unknown(),
        activeScreen = "Dashboard"
    )

    private fun render(width: Int) =
        LandingRenderer(theme).render(state(), width, 24).map(TerminalText::stripAnsi)

    @Test
    fun nothing_is_cut_off_on_a_phone_sized_screen() {
        listOf(30, 36, 46).forEach { width ->
            val lines = render(width)

            val truncated = lines.filter { it.contains('…') }
            assertTrue(
                truncated.isEmpty(),
                "at $width cells these lines are unreadable:\n" + truncated.joinToString("\n")
            )
        }
    }

    @Test
    fun nothing_overruns_the_frame() {
        listOf(30, 36, 46, 80).forEach { width ->
            render(width).forEach { line ->
                assertTrue(
                    TerminalText.cellWidth(line) <= width,
                    "at $width cells a line rendered ${TerminalText.cellWidth(line)}: '$line'"
                )
            }
        }
    }

    @Test
    fun a_narrow_screen_stacks_the_label_above_its_meaning() {
        val lines = render(30)

        // The command on its own line, the purpose indented under it, so the
        // width is spent on meaning rather than on alignment.
        val command = lines.indexOfFirst { it.trim() == "/factory run <prompt>" }
        assertTrue(command >= 0, "the starter row is missing:\n" + lines.joinToString("\n"))
        assertTrue(lines[command + 1].startsWith("    "), "the purpose is not stacked under it")
    }

    @Test
    fun a_wide_screen_keeps_the_two_column_table() {
        // Stacking everywhere would waste the space a desktop actually has.
        val lines = render(80)

        assertTrue(
            lines.any { it.contains("/factory run <prompt>") && it.contains("the whole build") },
            "the wide layout lost its columns:\n" + lines.joinToString("\n")
        )
    }

    @Test
    fun words_are_never_broken_in_half() {
        // A reader should not have to reassemble "re" and "search" across two
        // lines before they can read the row.
        val lines = render(30)

        // Every word of the wrapped purpose survives intact on some line.
        // Asserting on how the text happens to end would catch "here" as a
        // broken "re", which is the mistake this assertion replaces.
        val rendered = lines.joinToString("\n")
        listOf("atoms,", "research,", "code,", "proof").forEach { word ->
            assertTrue(
                lines.any { line -> line.split(' ').any { it == word } },
                "'$word' was broken across lines:\n$rendered"
            )
        }
    }

    @Test
    fun the_factory_is_not_described_as_only_making_a_dag() {
        // The DAG is stage five of eight. Calling the factory "turn a document
        // into a DAG" told an operator the build stops there.
        val text = render(80).joinToString(" ")

        assertTrue(text.contains("/factory run"), text)
        assertFalse(
            text.contains("into a DAG"),
            "the factory is still described as a DAG generator: $text"
        )
    }
}
