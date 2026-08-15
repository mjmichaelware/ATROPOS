/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.ui

import atropos.cli.config.ConfigurationManager
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class QuotaFuelCellRendererTest {
    private val theme = TerminalTheme(ConfigurationManager(envProvider = { "true" }, hasConsole = false))
    private val renderer = QuotaFuelCellRenderer(theme)

    @Test
    fun `renders locked when over limit`() {
        val state = QuotaFuelCellRenderer.QuotaState(150.0, 100.0)
        val output = renderer.render(state, 40)
        assertTrue(output.contains("LOCKED : QUOTA EXCEEDED"))
    }

    @Test
    fun `renders fill and ghost proportional to usage`() {
        val state = QuotaFuelCellRenderer.QuotaState(50.0, 100.0, 10.0)
        val output = renderer.render(state, 20)
        assertTrue(output.contains("█")) // used
        assertTrue(output.contains("▒")) // projected
        assertTrue(output.contains("░")) // empty
    }
}
