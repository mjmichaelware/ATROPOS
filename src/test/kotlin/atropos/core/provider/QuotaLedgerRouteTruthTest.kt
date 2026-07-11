package atropos.core.provider

import atropos.cli.ui.StatusQuotaRenderer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import java.nio.file.Files

class QuotaLedgerRouteTruthTest {
    @Test
    fun quota_ledger_persists_failure_state_and_route_falls_back() {
        val temp = Files.createTempDirectory("atropos-quota-route")
        val registry = StaticProviderDescriptorRegistry()
        val seed = FileQuotaLedger.seedFromDescriptors(registry)
        val ledger = FileQuotaLedger(temp.resolve("quota.tsv").toFile(), seed)
        ledger.recordFailure("groq", ProviderFailure("groq", NormalizedProviderFailureType.RATE_LIMITED, "groq rate limited", retryAfterMs = 60_000), 1_000L)

        val reopened = FileQuotaLedger(temp.resolve("quota.tsv").toFile(), seed)
        assertEquals(ProviderAvailabilityState.COOLDOWN, reopened.get("groq")?.state)

        val decision = RoutePolicy(
            registry = registry,
            ledger = reopened,
            costPolicy = AtroposCostPolicy.FREE_ONLY,
            nowEpochMs = { 2_000L }
        ).decide(ProviderTask(ProviderTaskKind.FAST_CODE_DRAFT, ApiCapability.CODE, "fix compile error"))

        assertTrue(decision.selectedProviderId != "groq")
        assertTrue(decision.skipped.any { it.provider.id == "groq" && it.reason == "cooldown" })
    }

    @Test
    fun route_explanation_reports_selected_skipped_fallback_cooldown_reset_paid_lock_and_outcome() {
        val registry = StaticProviderDescriptorRegistry()
        val seed = FileQuotaLedger.seedFromDescriptors(registry)
        val ledger = InMemoryQuotaLedger(seed)
        ledger.recordFailure(
            "groq",
            ProviderFailure("groq", NormalizedProviderFailureType.RATE_LIMITED, "groq rate limited", retryAfterMs = 60_000),
            1_000L
        )
        ledger.recordFailure(
            "deepinfra",
            ProviderFailure("deepinfra", NormalizedProviderFailureType.QUOTA_EXHAUSTED, "deepinfra quota exhausted", resetAtEpochMs = 70_000L),
            1_000L
        )

        val report = StatusQuotaRenderer(
            registry = registry,
            ledger = ledger,
            nowEpochMs = { 2_000L },
            costPolicy = AtroposCostPolicy.FREE_ONLY
        ).renderRoute("fix compile error")

        assertTrue(report.contains("selected: openrouter"), report)
        assertTrue(report.contains("final outcome: selected openrouter"), report)
        assertTrue(report.contains("fallback reason:"), report)
        assertTrue(report.contains("groq:cooldown"), report)
        assertTrue(report.contains("groq") && report.contains("reason=cooldown"), report)
        assertTrue(report.contains("deepinfra") && report.contains("reason=blocked_by_cost_policy"), report)
        assertTrue(report.contains("reset=") && !report.lineContaining("deepinfra").contains("reset=-"), report)
        assertTrue(report.contains("cooldown=") && !report.lineContaining("groq").contains("cooldown=-"), report)
        assertTrue(report.contains("openai") && report.contains("paid_locked=true"), report)
    }

    private fun String.lineContaining(needle: String): String =
        lines().firstOrNull { it.contains(needle) }.orEmpty()
}
