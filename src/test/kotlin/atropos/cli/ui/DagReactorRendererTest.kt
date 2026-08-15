/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.ui

import atropos.cli.config.ConfigurationManager
import atropos.cli.ui.design.HoeStatusVocabulary
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DagReactorRendererTest {
    private val theme = TerminalTheme(ConfigurationManager(envProvider = { "true" }, hasConsole = false))
    private val renderer = DagReactorRenderer(theme)

    @Test
    fun `working node ignites and swells`() {
        val nodes = listOf(DagReactorRenderer.ReactorNode("node1", HoeStatusVocabulary.WORKING, "compiling", 0.5))
        val output = renderer.render(nodes, 80)
        assertTrue(output.isNotEmpty())
        assertTrue(output[0].contains("node1"))
        assertTrue(output[0].contains("50%"))
        assertTrue(output[0].contains("compiling"))
    }
}
