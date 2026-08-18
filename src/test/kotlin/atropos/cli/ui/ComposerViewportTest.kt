package atropos.cli.ui

import atropos.cli.config.ConfigurationManager
import atropos.cli.ui.design.ColorTier
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class ComposerViewportTest {
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
