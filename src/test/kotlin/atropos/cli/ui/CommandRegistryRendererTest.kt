package atropos.cli.ui

import atropos.cli.config.ConfigurationManager
import atropos.cli.ui.design.ColorTier
import kotlin.test.Test
import kotlin.test.assertTrue

class CommandRegistryRendererTest {
    @Test
    fun slash_help_renderer_uses_registry_alias_groups() {
        val renderer = CommandRegistryRenderer(
            TerminalTheme(ConfigurationManager(), tierOverride = ColorTier.NONE)
        )

        val plain = renderer.renderSlashCommands(120).joinToString("\n")

        assertTrue(plain.contains("System"), plain)
        assertTrue(plain.contains("Self-host"), plain)
        assertTrue(plain.contains("/help"), plain)
        assertTrue(plain.contains("/usage"), plain)
        assertTrue(plain.contains("/?"), plain)
        assertTrue(plain.contains("/self-host"), plain)
        assertTrue(plain.contains("/agent self-host"), plain)
    }
}
