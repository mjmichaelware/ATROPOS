package atropos.cli

import atropos.core.ApiKeys
import atropos.core.AtroposConfig
import atropos.core.LakehouseConfig
import atropos.core.RuntimeConfig
import atropos.core.integration.McpHostManager
import atropos.core.provider.ProviderOnboardingService
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertTrue

class BackendDoctorTest {
    @Test
    fun doctor_composes_provider_and_mcp_truth_without_secrets() {
        val root = Files.createTempDirectory("backend-doctor")
        val config = AtroposConfig(
            ApiKeys("", "", "", ""),
            LakehouseConfig(root.toString(), root.resolve("db").toString()),
            RuntimeConfig("groq", 0.2, localOnly = true)
        )
        val output = BackendDoctor(
            config,
            providers = ProviderOnboardingService(root, emptyMap()),
            mcp = McpHostManager(root)
        ).render().joinToString("\n")
        assertTrue(output.contains("local_only=true"))
        assertTrue(output.contains("providers:"))
        assertTrue(output.contains("mcp:"))
        assertTrue(!output.contains("API_KEY="))
    }

    @Test
    fun doctor_exposes_zero_retention_research_mode() {
        val root = Files.createTempDirectory("backend-doctor-zero-retention")
        val config = AtroposConfig(
            ApiKeys("", "", "", ""),
            LakehouseConfig(root.toString(), root.resolve("db").toString()),
            RuntimeConfig("local", 0.2, zeroRetentionResearch = true)
        )
        val output = BackendDoctor(
            config,
            providers = ProviderOnboardingService(root, emptyMap()),
            mcp = McpHostManager(root)
        ).render().joinToString("\n")
        assertTrue(output.contains("zero_retention_research=true"))
    }
}
