/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.ui

import atropos.cli.config.ConfigurationManager
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BottomStatusBarTest {

    private val theme = TerminalTheme(ConfigurationManager())
    private val bar = BottomStatusBar(theme)

    @Test
    fun `render produces single line with exact width and path and provider`() {
        val width = 80
        val output = bar.render(
            workspacePath = "/home/user/project",
            branch = "main",
            provider = "gemini",
            progressPercent = 45,
            costUsd = 0.0123,
            width = width
        )

        val stripped = TerminalText.stripAnsi(output)
        assertEquals(width, TerminalText.cellWidth(stripped))
        assertTrue(stripped.contains("project") || stripped.contains("~"))
        assertTrue(stripped.contains("gemini"))
        assertTrue(stripped.contains("45%"))
        assertTrue(stripped.contains("0.0123"))
    }

    @Test
    fun `render adapts gracefully when terminal width is narrow`() {
        val output = bar.render(
            workspacePath = "/home/user/project",
            branch = "main",
            provider = "gemini",
            progressPercent = 100,
            costUsd = 0.0,
            width = 30
        )
        val stripped = TerminalText.stripAnsi(output)
        assertEquals(30, TerminalText.cellWidth(stripped))
    }
}
