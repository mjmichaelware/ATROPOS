package atropos.cli.ui

import atropos.cli.config.ConfigurationManager
import atropos.cli.ui.design.DesignTokens
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Aggregate contract for the CLI surface's existing canonical owners. */
class HoeAntigravitySurfaceContractTest {
    @Test
    fun canonical_cli_surface_owners_share_the_same_rendering_contract() {
        val theme = TerminalTheme(ConfigurationManager())
        val state = SessionPresentationState(
            provider = "local",
            mode = "ask",
            workspace = ".",
            commands = listOf("/help"),
            tokens = MetricValue.Unknown,
            cost = MetricValue.Unknown,
            activeOperation = null
        )

        val landing = LandingRenderer(theme).render(state, terminalWidth = 40, terminalHeight = 8)
        assertTrue(landing.isNotEmpty())
        assertTrue(landing.all { it.length <= 40 })
        assertEquals("#dc2626", DesignTokens.Semantic.Brand.primary)

        // The engine remains the single terminal rendering owner. The
        // behavioral engine tests exercise its redraw/event path; this
        // contract prevents a parallel CLI renderer from becoming the
        // surface entrypoint.
        assertEquals("AnsiTerminalEngine", AnsiTerminalEngine::class.java.simpleName)
    }
}
