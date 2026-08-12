package atropos.cli

import atropos.cli.config.ConfigurationManager
import atropos.cli.session.QuotaSessionTracker
import atropos.cli.ui.AnsiTerminalEngine
import atropos.cli.ui.PlainTerminalOutput
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

class CommandRouterIdentityTest {
    @Test
    fun plain_atropos_probe_routes_to_deterministic_identity_not_provider_chat() {
        val root = Files.createTempDirectory("atropos-router-identity-")
        val out = ByteArrayOutputStream()
        var providerCalls = 0
        val router = CommandRouter(
            config = AtroposConfig(
                ApiKeys("", "", "", ""),
                LakehouseConfig(root.resolve("lakehouse").toString(), root.resolve("lakehouse/vector_storage.db").toString()),
                RuntimeConfig("fake", 0.2)
            ),
            uiEngine = AnsiTerminalEngine(
                capabilities = ConfigurationManager(),
                plainOutput = PlainTerminalOutput(
                    out = PrintStream(out),
                    errors = PrintStream(ByteArrayOutputStream())
                )
            ),
            sessionTracker = QuotaSessionTracker(),
            providerResolver = {
                object : AIProvider {
                    override val name: String = "fake"
                    override fun complete(prompt: String, context: String): String {
                        providerCalls += 1
                        return "Greek Fate"
                    }
                }
            }
        )

        val result = router.handleInput("ATROPOS")

        assertEquals(RouterOutcome.CONTINUE, result)
        assertEquals(0, providerCalls)
        val rendered = out.toString()
        assertTrue(rendered.contains("ATROPOS runtime state"), rendered)
        assertTrue(!rendered.contains("Greek Fate"), rendered)
    }
}
