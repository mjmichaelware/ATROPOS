package atropos.cli

import atropos.cli.config.ConfigurationManager
import atropos.cli.session.QuotaSessionTracker
import atropos.cli.ui.AnsiTerminalEngine
import atropos.core.AIProvider
import atropos.core.ApiKeys
import atropos.core.AtroposConfig
import atropos.core.LakehouseConfig
import atropos.core.RuntimeConfig
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CommandRouterHelpTest {
    @Test
    fun help_variants_render_help_without_provider_chat() {
        listOf("?", "/help", "/usage", "help", "/?").forEach { input ->
            val root = Files.createTempDirectory("atropos-router-help-")
            val out = ByteArrayOutputStream()
            var providerCalls = 0
            val router = newRouter(root, out) {
                providerCalls += 1
                "provider chat should not receive help"
            }

            val result = router.handleInput(input)

            assertEquals(RouterOutcome.CONTINUE, result, input)
            assertEquals(0, providerCalls, input)
            val rendered = out.toString()
            assertTrue(rendered.contains("COMMANDS"), rendered)
            assertTrue(rendered.contains("/help"), rendered)
        }
    }

    @Test
    fun self_host_shorthand_routes_to_self_host_status_without_provider_chat() {
        listOf("/self-host", "self-host").forEach { input ->
            val root = Files.createTempDirectory("atropos-router-self-host-")
            val out = ByteArrayOutputStream()
            var providerCalls = 0
            val router = newRouter(root, out) {
                providerCalls += 1
                "provider chat should not receive self-host shorthand"
            }

            val result = router.handleInput(input)

            assertEquals(RouterOutcome.CONTINUE, result, input)
            assertEquals(0, providerCalls, input)
            val rendered = out.toString()
            assertTrue(rendered.contains("SELF-HOST STATUS"), rendered)
        }
    }

    @Test
    fun self_host_help_renders_help_without_provider_chat() {
        val root = Files.createTempDirectory("atropos-router-self-host-help-")
        val out = ByteArrayOutputStream()
        var providerCalls = 0
        val router = newRouter(root, out) {
            providerCalls += 1
            "provider chat should not receive self-host help"
        }

        val result = router.handleInput("/self-host help")

        assertEquals(RouterOutcome.CONTINUE, result)
        assertEquals(0, providerCalls)
        val rendered = out.toString()
        assertTrue(rendered.contains("COMMANDS"), rendered)
        assertTrue(rendered.contains("/self-host"), rendered)
    }

    private fun newRouter(
        root: java.nio.file.Path,
        out: ByteArrayOutputStream,
        providerComplete: (String) -> String
    ): CommandRouter = CommandRouter(
        config = AtroposConfig(
            ApiKeys("", "", "", ""),
            LakehouseConfig(root.resolve("lakehouse").toString(), root.resolve("lakehouse/vector_storage.db").toString()),
            RuntimeConfig("fake", 0.2)
        ),
        uiEngine = AnsiTerminalEngine(
            capabilities = ConfigurationManager(),
            out = PrintStream(out),
            errors = PrintStream(ByteArrayOutputStream())
        ),
        sessionTracker = QuotaSessionTracker(),
        providerResolver = {
            object : AIProvider {
                override val name: String = "fake"
                override fun complete(prompt: String, context: String): String = providerComplete(prompt)
            }
        }
    )
}
