package atropos.cli.ui

import atropos.cli.config.ConfigurationManager
import atropos.cli.ui.design.ColorTier
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.test.assertNotNull

class ComposerViewportTest {

    private fun emptyComposer(): ComposerViewport =
        ComposerViewport(TerminalTheme(ConfigurationManager(), tierOverride = ColorTier.NONE))
            .also { it.update(buffer = "", suggestion = "", cursor = 0, mode = "ASK") }

    @Test
    fun the_empty_box_says_what_this_engine_does_rather_than_which_keys_work() {
        // The keybinding tip is on the opening screen a few rows above, and
        // saying it twice cost the most valuable line on the display. What that
        // line is worth saying is the pipeline, because this is not the tool it
        // looks like: a sentence typed here does not become a diff, it becomes
        // atoms, research, a DAG, a build and a proof. Someone expecting the
        // former has misunderstood the tool, and the box they are about to type
        // into is the honest place to prevent that.
        val rendered = emptyComposer().renderMultiline(90, 3).lines
            .joinToString("\n", transform = TerminalText::stripAnsi)

        listOf("prompt", "atoms", "research", "DAG", "build", "proof").forEach { stage ->
            assertTrue(stage in rendered, "the flow does not mention '$stage':\n$rendered")
        }
        assertFalse("for commands" in rendered, "the keybinding tip is still doubled here")
    }

    @Test
    fun a_narrow_box_drops_stages_rather_than_overflowing() {
        listOf(90, 60, 40, 30).forEach { width ->
            val rendered = emptyComposer().renderMultiline(width, 3).lines
                .map(TerminalText::stripAnsi)

            rendered.forEach { line ->
                assertTrue(
                    TerminalText.cellWidth(line) <= width,
                    "at $width cells the box rendered ${TerminalText.cellWidth(line)}: '$line'"
                )
            }
            // However narrow, what survives still says a build is the end of a
            // process rather than the start of one.
            assertTrue("prompt" in rendered.joinToString(" "), "no flow at all at $width cells")
        }
    }

    @Test
    fun command_query_surfaces_slashed_command_prefixes() {
        val viewport = ComposerViewport(
            TerminalTheme(
                ConfigurationManager(),
                tierOverride = ColorTier.NONE
            )
        )

        viewport.update(
            buffer = "/self-host",
            suggestion = "",
            cursor = 10,
            mode = "ASK"
        )

        val query = viewport.commandQuery()

        assertNotNull(query)
        assertEquals("/self-host", query.text)
    }

    /**
     * Deliberate contract change: an un-slashed word does not open the palette.
     *
     * It used to open on any word matching the registry, so typing `hi` buried
     * two thirds of the screen under eighteen commands nobody asked for. There
     * was no way to write an ordinary sentence without the palette in the way.
     */
    @Test
    fun ordinary_typing_leaves_the_palette_closed() {
        val viewport = ComposerViewport(
            TerminalTheme(atropos.cli.config.ConfigurationManager(envProvider = { null }, hasConsole = false))
        )

        listOf("hi", "self-host", "status", "help").forEach { word ->
            viewport.update(buffer = word, suggestion = "", cursor = word.length, mode = "ASK")
            kotlin.test.assertNull(
                viewport.commandQuery(),
                "typing '$word' opened the command palette"
            )
        }
    }
}
