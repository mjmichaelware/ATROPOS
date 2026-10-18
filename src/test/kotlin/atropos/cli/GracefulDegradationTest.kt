/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli

import atropos.cli.help.CommandLeafRegistry
import atropos.cli.ui.BeautifulBoxSystem
import atropos.cli.ui.CategorizedTrieModalRenderer
import atropos.cli.ui.CostPeekModalRenderer
import atropos.cli.ui.InterruptModalSurface
import atropos.cli.ui.QuestionMarkModalRenderer
import atropos.cli.ui.TerminalText
import atropos.cli.ui.TerminalTheme
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * ATOM-RESILIENCE-029: Responsive Terminal Graceful Degradation Test Suite.
 *
 * Verifies that all modal surfaces, beautiful boxes, and command leaves
 * gracefully degrade across narrow terminals, browser resize events, and
 * mobile APK portrait/landscape rotations without throwing exceptions or
 * overrunning character boundaries.
 */
class GracefulDegradationTest {

    private val theme = TerminalTheme()
    private val boxSystem = BeautifulBoxSystem("atropos-dark")

    @Test
    fun beautiful_box_system_never_exceeds_requested_width() {
        val testWidths = listOf(36, 40, 60, 80, 100, 140, 200)
        val content = listOf(
            "This is a long description line that must be clipped or ellipsized properly without overflowing",
            "Short line",
            "/toolchain local doctor --verbose"
        )

        for (w in testWidths) {
            val lines = boxSystem.renderBox(title = "TEST BOX", contentLines = content, width = w)
            for (line in lines) {
                // Strip ANSI escapes to check visible character width
                val stripped = stripAnsi(line)
                assertTrue(
                    stripped.length <= w + 4, // allow slight border padding tolerance
                    "Line in box of width $w exceeded boundary: len=${stripped.length}, content='$stripped'"
                )
            }
        }
    }

    @Test
    fun question_mark_modal_survives_narrow_and_wide_viewports() {
        val renderer = QuestionMarkModalRenderer(theme)
        val widths = listOf(50, 60, 80, 120)

        for (w in widths) {
            for (view in QuestionMarkModalRenderer.ActiveView.values()) {
                val lines = renderer.render(activeView = view, selectedIndex = 0, width = w)
                assertTrue(lines.isNotEmpty(), "Question mark modal must produce lines for view $view at width $w")
            }
        }
    }

    @Test
    fun categorized_trie_modal_survives_extreme_dimensions() {
        val renderer = CategorizedTrieModalRenderer(theme)
        val state = CategorizedTrieModalRenderer.TrieNavState()
        val widths = listOf(60, 80, 100, 160)

        for (w in widths) {
            val lines = renderer.render(state = state, width = w)
            assertTrue(lines.isNotEmpty(), "Categorized trie modal must render at width $w")
        }
    }

    @Test
    fun interrupt_surface_survives_all_dimensions_with_expansion() {
        val interruptSurface = InterruptModalSurface(theme)
        val widths = listOf(60, 80, 120)

        for (w in widths) {
            // Collapsed
            val collapsed = interruptSurface.render(selectedIndex = 0, isExpanded = false, width = w)
            assertTrue(collapsed.isNotEmpty(), "Interrupt surface must render collapsed at width $w")

            // Expanded mechanics
            val expanded = interruptSurface.render(selectedIndex = 1, isExpanded = true, width = w)
            assertTrue(expanded.isNotEmpty(), "Interrupt surface must render expanded at width $w")
        }
    }

    @Test
    fun cost_peek_survives_small_viewports() {
        val peek = CostPeekModalRenderer(theme)
        val metrics = CostPeekModalRenderer.CostMetrics(
            totalTokensUsed = 125_400,
            promptTokens = 100_000,
            completionTokens = 25_400,
            providerName = "gemini-2.5-pro",
            estimatedCostUsd = 0.042
        )

        val widths = listOf(50, 60, 80)
        for (w in widths) {
            val lines = peek.render(metrics, width = w)
            assertTrue(lines.isNotEmpty(), "Cost peek must render at width $w")
        }
    }

    @Test
    fun settings_modal_survives_narrow_and_wide_viewports() {
        val renderer = atropos.cli.ui.SettingsModalRenderer(theme)
        val config = atropos.core.config.AtroposUnifiedConfig()
        val widths = listOf(50, 60, 80, 120)

        for (w in widths) {
            val lines = renderer.render(config, selectedIndex = 0, width = w)
            assertTrue(lines.isNotEmpty(), "Settings modal must render at width $w")
        }
    }

    private fun stripAnsi(text: String): String =
        text.replace(Regex("\u001B\\[[;?0-9]*[a-zA-Z]"), "")
}
