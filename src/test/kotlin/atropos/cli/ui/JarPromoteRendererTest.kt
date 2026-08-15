/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.ui

import kotlin.test.*

import atropos.cli.config.ConfigurationManager

class JarPromoteRendererTest {
    private val theme = TerminalTheme(ConfigurationManager(envProvider = { "true" }, hasConsole = false))
    private val renderer = JarPromoteRenderer(theme)

    @Test
    fun `renders seated when verified`() {
        val output = renderer.render("oldhash123", "newhash456", true)
        assertTrue(output.any { it.contains("SEATED") })
        assertTrue(output.any { it.contains("newhash4") })
    }

    @Test
    fun `renders blocked when unverified`() {
        val output = renderer.render(null, "newhash456", false)
        assertTrue(output.any { it.contains("BLOCKED (Unverified)") })
    }
}
