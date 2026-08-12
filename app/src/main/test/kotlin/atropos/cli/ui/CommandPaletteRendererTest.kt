package atropos.cli.ui

import atropos.cli.config.ConfigurationManager
import atropos.cli.input.CommandPaletteLevel
import atropos.cli.ui.design.ColorTier
import kotlin.test.Test
import kotlin.test.assertNotEquals
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

    @Test
    fun selected_row_uses_readable_light_foreground_on_red_background() {
        val renderer = CommandPaletteRenderer(
            TerminalTheme(ConfigurationManager(), tierOverride = ColorTier.TRUECOLOR)
        )
        val selected = renderer.render(CommandPaletteQuery("help", 0), 80, 5).first { it.contains("/help") }
        assertTrue(selected.contains("38;2;255;255;255"), selected)
        assertTrue(selected.contains("48;2;196;0;29"), selected)
    }

    @Test
    fun help_aliases_render_group_level_with_pinned_quick_access() {
        val renderer = CommandPaletteRenderer(
            TerminalTheme(ConfigurationManager(), tierOverride = ColorTier.NONE)
        )
        val lines = renderer.render(
            CommandPaletteQuery("?", level = CommandPaletteLevel.GROUPS),
            width = 100,
            maximumRows = 8
        )
        val text = lines.joinToString("\n")
        assertTrue(text.contains("Pinned:"), text)
        assertTrue(text.contains("Models"), text)
        assertTrue(text.contains("Self-host"), text)
    }

    @Test
    fun selected_command_scrolls_through_results_beyond_visible_rows() {
        val renderer = CommandPaletteRenderer(
            TerminalTheme(ConfigurationManager(), tierOverride = ColorTier.NONE)
        )
        val first = renderer.render(
            CommandPaletteQuery("agent", selectedIndex = 0),
            width = 100,
            maximumRows = 5
        )
        val lines = renderer.render(
            CommandPaletteQuery("agent", selectedIndex = 10),
            width = 100,
            maximumRows = 5
        )
        val text = lines.joinToString("\n")
        assertTrue(text.contains("/agent"), text)
        assertNotEquals(first, lines, "selection must scroll the result window")
    }
}
