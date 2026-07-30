package atropos.cli.ui

import atropos.cli.config.ConfigurationManager
import atropos.cli.ui.design.ColorTier
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class ComposerViewportTest {
    @Test
    fun command_query_surfaces_bare_command_prefixes() {
        val viewport = ComposerViewport(
            TerminalTheme(
                ConfigurationManager(),
                tierOverride = ColorTier.NONE
            )
        )

        viewport.update(
            buffer = "self-host",
            suggestion = "",
            cursor = 9,
            mode = "ASK"
        )

        val query = viewport.commandQuery()

        assertNotNull(query)
        assertEquals("self-host", query.text)
    }
}
