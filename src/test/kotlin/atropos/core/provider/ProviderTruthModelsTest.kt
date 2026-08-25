package atropos.core.provider

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import atropos.core.ApiKeys
import atropos.core.AtroposConfig
import atropos.core.LakehouseConfig
import atropos.core.RuntimeConfig
import java.nio.file.Files

class ProviderTruthModelsTest {
    private fun snapshot() = ProviderTruthSnapshot(
        selectedProvider = "groq",
        records = listOf(
            ProviderTruthRecord(
                id = "groq",
                category = "llm",
                costMode = CostMode.FREE,
                keyPresent = true,
                descriptorPresent = true,
                adapterPresent = true,
                executableSupport = true,
                health = ProviderAvailabilityState.READY,
                askEligible = true,
                patchEligible = true,
                paidLocked = false,
                missingRequirements = emptyList()
            ),
            ProviderTruthRecord(
                id = "openai",
                category = "llm",
                costMode = CostMode.PAID_LOCKED,
                keyPresent = false,
                descriptorPresent = true,
                adapterPresent = true,
                executableSupport = false,
                health = ProviderAvailabilityState.DISABLED,
                askEligible = false,
                patchEligible = false,
                paidLocked = true,
                missingRequirements = listOf("OPENAI_API_KEY")
            )
        ),
        askOrder = listOf("groq"),
        patchOrder = listOf("groq"),
        lastActualProvider = null,
        paidAutomaticModeLocked = true
    )

    @Test
    fun compact_inventory_groups_rows_and_marks_active_provider() {
        val rendered = snapshot().renderInventory()

        assertTrue(rendered.contains("PROVIDERS"))
        assertTrue(rendered.contains("> groq"))
        assertTrue(rendered.contains("LLM"))
        assertTrue(rendered.contains("[READY]"))
        assertTrue(rendered.contains("details: /providers --full"))
        assertFalse(rendered.contains("requirements: OPENAI_API_KEY"))
    }

    @Test
    fun expanded_inventory_keeps_compact_rows_and_reveals_requirements() {
        val rendered = snapshot().renderInventory(expanded = true)

        assertTrue(rendered.contains("caps:"))
        assertTrue(rendered.contains("requirements: OPENAI_API_KEY"))
        assertTrue(rendered.contains("end of provider inventory"))
    }

    @Test
    fun truth_projection_excludes_provider_disabled_in_launch_inventory() {
        val root = Files.createTempDirectory("provider-truth-disabled")
        val onboarding = ProviderOnboardingService(
            root = root,
            environment = mapOf("GROQ_API_KEY" to "fixture-secret")
        )
        onboarding.refresh()
        onboarding.disable("groq")

        val config = AtroposConfig(
            ApiKeys("fixture-secret", "", "", ""),
            LakehouseConfig("/tmp/atropos-test", "/tmp/atropos-test/vector.db"),
            RuntimeConfig("groq", 0.2)
        )
        val truth = ProviderTruthService(
            config = config,
            onboarding = onboarding,
            ollamaProbe = { false }
        ).snapshot()

        assertFalse(truth.askOrder.contains("groq"))
        assertFalse(truth.patchOrder.contains("groq"))
    }
}
