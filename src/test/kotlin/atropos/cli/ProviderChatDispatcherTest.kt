package atropos.cli

import atropos.cli.config.ConfigurationManager
import atropos.cli.session.QuotaSessionTracker
import atropos.cli.ui.AnsiTerminalEngine
import atropos.cli.ui.PlainTerminalOutput
import atropos.core.AIProvider
import atropos.core.ApiKeys
import atropos.core.AtroposConfig
import atropos.core.LakehouseConfig
import atropos.core.ProviderFactory
import atropos.core.provider.ProviderCascadeRouter
import atropos.core.RuntimeConfig
import atropos.core.paid.EmergencyPaidGate
import atropos.core.provider.ApiCapability
import atropos.core.provider.CostMode
import atropos.core.provider.ProviderDescriptor
import atropos.core.provider.ProviderDescriptorRegistry
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProviderChatDispatcherTest {

    @Test
    fun conversational_reply_is_rendered_raw_without_attestation_or_diff_validation() {
        val root = Files.createTempDirectory("atropos-chat-dispatch-")
        val out = ByteArrayOutputStream()
        val config = AtroposConfig(
            ApiKeys("", "", "", ""),
            LakehouseConfig(root.resolve("lakehouse").toString(), root.resolve("lakehouse/vector.db").toString()),
            RuntimeConfig("fake", 0.2)
        )
        val prompts = mutableListOf<String>()
        val contexts = mutableListOf<String>()
        val descriptor = ProviderDescriptor(
            id = "fake",
            displayName = "Fake",
            costMode = CostMode.FREE,
            quotaTier = 1,
            capabilities = setOf(ApiCapability.CHAT)
        )
        val cascade = ProviderCascadeRouter(
            factory = ProviderFactory(config),
            providerResolver = { provider ->
                object : AIProvider {
                    override val name: String = provider
                    override fun complete(prompt: String, context: String): String {
                        prompts += prompt
                        contexts += context
                        return "Hello! How can I help you today?"
                    }
                }
            },
            registry = SingleProviderRegistry(descriptor),
            localHealth = { true },
            healthyProviderIds = { setOf("fake") },
            preferredProviderIds = { listOf("fake") },
            localOnly = { false },
            paidGate = EmergencyPaidGate(Files.createTempDirectory("atropos-chat-paid").toFile())
        )
        val dispatcher = ProviderChatDispatcher(
            config = config,
            uiEngine = AnsiTerminalEngine(
                capabilities = ConfigurationManager(),
                plainOutput = PlainTerminalOutput(
                    out = PrintStream(out),
                    errors = PrintStream(ByteArrayOutputStream())
                )
            ),
            sessionTracker = QuotaSessionTracker(),
            rateResolver = { 0.0 },
            cascadeRouter = cascade
        )

        dispatcher.dispatch("hi", "fake")

        val rendered = out.toString()
        assertEquals(listOf("hi"), prompts)
        assertEquals(listOf(""), contexts)
        assertTrue(rendered.contains("Hello! How can I help you today?"), rendered)
        assertFalse(rendered.contains("attestation", ignoreCase = true), rendered)
        assertFalse(rendered.contains("no unified diff found", ignoreCase = true), rendered)
    }

    private class SingleProviderRegistry(
        private val descriptor: ProviderDescriptor
    ) : ProviderDescriptorRegistry {
        override fun getAll(): List<ProviderDescriptor> = listOf(descriptor)

        override fun getById(id: String): ProviderDescriptor? = descriptor.takeIf { it.id == id }

        override fun getFreeEligible(): List<ProviderDescriptor> = listOf(descriptor)

        override fun getPaidLocked(): List<ProviderDescriptor> = emptyList()

        override fun getByCapability(capability: ApiCapability): List<ProviderDescriptor> =
            listOf(descriptor).filter { it.hasCapability(capability) }
    }
}
