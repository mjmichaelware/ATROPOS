package atropos.cli

import atropos.core.ApiKeys
import atropos.core.AtroposConfig
import atropos.core.LakehouseConfig
import atropos.core.RuntimeConfig
import atropos.core.provider.ProviderOnboardingService
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertTrue

class FirstRunProbeTest {
    @Test
    fun first_run_uses_shared_onboarding_alias_inventory() {
        val root = Files.createTempDirectory("atropos-first-run-probe-")
        val onboarding = ProviderOnboardingService(
            root = root,
            environment = mapOf("CLAUDE_API_KEY" to "fixture-secret")
        )
        onboarding.refresh()

        val config = AtroposConfig(
            ApiKeys("", "", "", ""),
            LakehouseConfig(root.resolve("lakehouse").toString(), root.resolve("db").toString()),
            RuntimeConfig("anthropic", 0.2)
        )
        val progress = FirstRunProbe(
            config = config,
            workspace = root,
            environment = { null },
            onboarding = onboarding
        ).progress()

        assertTrue(progress.providerConfigured)
    }
}
