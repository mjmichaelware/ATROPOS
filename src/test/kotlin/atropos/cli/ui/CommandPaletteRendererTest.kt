package atropos.cli.ui

import atropos.cli.config.ConfigurationManager
import atropos.cli.ui.design.ColorTier
import kotlin.test.Test
import kotlin.test.assertTrue

class CommandPaletteRendererTest {
    @Test
    fun renders_palette_for_bare_command_prefixes() {
        val renderer = CommandPaletteRenderer(
            TerminalTheme(
                ConfigurationManager(),
                tierOverride = ColorTier.NONE
            )
        )

        val lines = renderer.render(
            CommandPaletteQuery("help"),
            width = 80,
            maximumRows = 5
        )

        assertTrue(lines.isNotEmpty())
        assertTrue(lines.any { it.contains("/help") }, lines.joinToString("\n"))
    }
}
