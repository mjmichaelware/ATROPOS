/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.ui

import atropos.cli.config.ConfigurationManager
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProviderSummaryRendererTest {
    private val renderer = ProviderSummaryRenderer(TerminalTheme(ConfigurationManager()))

    @Test
    fun renderCompactHealthy() {
        val health = ProviderSummaryRenderer.ProviderHealth(
            name = "claude-3-5-sonnet",
            status = "healthy",
            costUsd = 0.001,
            quotaPercent = 45
        )
        val line = renderer.renderCompact("sonnet", emptyList(), health, 60)

        assertTrue(line.contains("●"), "Should show healthy indicator")
        assertTrue(line.contains("claude-3-5-sonnet"), "Should show provider name")
        assertTrue(line.length <= 60, "Should respect width")
    }

    @Test
    fun renderCompactDegraded() {
        val health = ProviderSummaryRenderer.ProviderHealth(
            name = "gpt-4",
            status = "degraded",
            costUsd = 0.05,
            quotaPercent = 75
        )
        val line = renderer.renderCompact("gpt-4", emptyList(), health, 60)

        assertTrue(line.contains("◑"), "Should show degraded indicator")
    }

    @Test
    fun renderFullMatrix() {
        val providers = listOf(
            ProviderSummaryRenderer.ProviderHealth("claude-3-5-sonnet", "healthy", 0.001, 45, 12),
            ProviderSummaryRenderer.ProviderHealth("gpt-4", "degraded", 0.05, 92, 8),
            ProviderSummaryRenderer.ProviderHealth("o1", "unhealthy", 0.0, 0, 0)
        )
        val lines = renderer.renderFull(providers, 80)

        assertEquals(4, lines.size, "Should have header + 3 providers")
        assertTrue(lines[0].contains("Matrix"), "First line should be header")
        assertTrue(lines[1].contains("●"), "Should show health indicator")
    }

    @Test
    fun respectWidth() {
        val health = ProviderSummaryRenderer.ProviderHealth(
            name = "very-long-provider-name-that-exceeds-width",
            status = "healthy",
            costUsd = 0.123456,
            quotaPercent = 95
        )
        val line = renderer.renderCompact("long", emptyList(), health, 40)
        assertEquals(40, line.length, "Should respect width exactly")
    }
}
