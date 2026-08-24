package atropos.core.provider

import atropos.cli.ui.StatusQuotaRenderer
import atropos.core.paid.EmergencyPaidGate
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
        ledger.put(readyRemote(seed, "groq"))
        ledger.put(readyRemote(seed, "openrouter"))
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
    fun quota_backup_copies_live_ledger_instead_of_descriptor_seed() {
        val root = Files.createTempDirectory("atropos-quota-backup")
        val registry = StaticProviderDescriptorRegistry()
        val seed = FileQuotaLedger.seedFromDescriptors(registry)
        val source = root.resolve("quota.tsv").toFile()
        val ledger = FileQuotaLedger(source, seed)
        ledger.put(readyRemote(seed, "groq"))
        ledger.recordFailure(
            "groq",
            ProviderFailure("groq", NormalizedProviderFailureType.RATE_LIMITED, "rate limited", retryAfterMs = 60_000),
            1_000L
        )

        val target = root.resolve("backup.tsv").toFile()
        val result = QuotaLedgerBackup(registry, root.resolve("backups").toFile(), source).backup(target)
        assertEquals(ledger.all().size, result.records)
        assertTrue(target.readText().contains("groq\tFREE\t1\ttrue\ttrue\tCOOLDOWN"))
    }

    @Test
    fun route_explanation_reports_selected_skipped_fallback_cooldown_reset_paid_lock_and_outcome() {
        val registry = StaticProviderDescriptorRegistry()
        val seed = FileQuotaLedger.seedFromDescriptors(registry)
        val ledger = InMemoryQuotaLedger(seed)
        ledger.put(readyRemote(seed, "openrouter"))
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
        assertTrue(report.contains("groq:not_configured"), report)
        assertTrue(report.contains("groq") && report.contains("reason=not_configured"), report)
        assertTrue(report.contains("deepinfra") && report.contains("reason=blocked_by_cost_policy"), report)
        assertTrue(report.contains("reset=") && !report.lineContaining("deepinfra").contains("reset=-"), report)
        assertTrue(report.contains("cooldown=") && !report.lineContaining("groq").contains("cooldown=-"), report)
        assertTrue(report.contains("openai") && report.contains("paid_locked=true"), report)
    }

    @Test
    fun route_queues_when_only_descriptor_present_remote_providers_match() {
        val registry = StaticProviderDescriptorRegistry()
        val seed = FileQuotaLedger.seedFromDescriptors(registry).map {
            if (it.providerId == "ollama" || it.providerId == "local") {
                it.copy(state = ProviderAvailabilityState.OFFLINE)
            } else {
                it
            }
        }
        val decision = RoutePolicy(
            registry = registry,
            ledger = InMemoryQuotaLedger(seed),
            costPolicy = AtroposCostPolicy.FREE_ONLY
        ).decide(ProviderTask(ProviderTaskKind.FAST_CODE_DRAFT, ApiCapability.CODE, "write source"))

        assertEquals(null, decision.selectedProviderId)
        assertTrue(decision.queued)
        assertTrue(decision.degraded)
        assertTrue(decision.skipped.any { it.reason == "not_configured" || it.reason == "not_verified" })
    }

    @Test
    fun route_prioritizes_free_eligible_providers_first() {
        val temp = Files.createTempDirectory("atropos-quota-free-first")
        val registry = StaticProviderDescriptorRegistry()
        val seed = FileQuotaLedger.seedFromDescriptors(registry)
        val ledger = InMemoryQuotaLedger(seed)

        // Mark both groq (free) and openai (paid) as ready
        ledger.put(readyRemote(seed, "groq"))
        ledger.put(readyRemote(seed, "openai").copy(paidLocked = false)) // pretend unlocked for cost guard

        val paidGate = EmergencyPaidGate(temp.resolve("paid").toFile())
        val decision = RoutePolicy(
            registry = registry,
            ledger = ledger,
            costPolicy = AtroposCostPolicy.PAID_EMERGENCY_UNLOCKED, // allows both
            paidGate = paidGate
        ).decide(ProviderTask(ProviderTaskKind.CHAT_PROMPT, ApiCapability.CHAT, "hello"))

        // Even though openai is higher priority in taskPriority, groq should be selected because it is FREE (not PAID_LOCKED)
        assertEquals("groq", decision.selectedProviderId)
    }

    @Test
    fun local_only_route_excludes_remote_candidates() {
        val registry = StaticProviderDescriptorRegistry()
        val seed = FileQuotaLedger.seedFromDescriptors(registry)
        val ledger = InMemoryQuotaLedger(seed)
        ledger.put(readyRemote(seed, "ollama"))
        ledger.put(readyRemote(seed, "groq"))

        val decision = RoutePolicy(
            registry = registry,
            ledger = ledger,
            costPolicy = AtroposCostPolicy.FREE_ONLY,
            localOnly = true
        ).decide(ProviderTask(ProviderTaskKind.CHAT_PROMPT, ApiCapability.CHAT, "hello"))

        assertEquals("ollama", decision.selectedProviderId)
        assertTrue(decision.skipped.any { it.provider.id == "groq" && it.reason == "blocked_by_local_only" })
    }

    @Test
    fun healthy_set_and_preference_supplier_control_route_selection() {
        val registry = StaticProviderDescriptorRegistry()
        val seed = FileQuotaLedger.seedFromDescriptors(registry)
        val ledger = InMemoryQuotaLedger(seed)
        ledger.put(readyRemote(seed, "groq"))
        ledger.put(readyRemote(seed, "openrouter"))

        val preferred = RoutePolicy(
            registry = registry,
            ledger = ledger,
            costPolicy = AtroposCostPolicy.FREE_ONLY,
            healthyProviderIds = { setOf("groq", "openrouter") },
            preferredProviderIds = { listOf("openrouter", "groq") }
        ).decide(ProviderTask(ProviderTaskKind.CHAT_PROMPT, ApiCapability.CHAT, "hello"))
        assertEquals("openrouter", preferred.selectedProviderId)

        val disabled = RoutePolicy(
            registry = registry,
            ledger = ledger,
            costPolicy = AtroposCostPolicy.FREE_ONLY,
            healthyProviderIds = { setOf("openrouter") }
        ).decide(ProviderTask(ProviderTaskKind.CHAT_PROMPT, ApiCapability.CHAT, "hello"))
        assertEquals("openrouter", disabled.selectedProviderId)
        assertTrue(disabled.skipped.any { it.provider.id == "groq" && it.reason == "not_in_healthy_set" })
    }

    @Test
    fun emergency_paid_gate_bypass_allows_unlocked_paid_provider() {
        val temp = Files.createTempDirectory("atropos-paid-bypass")
        val registry = StaticProviderDescriptorRegistry()
        val seed = FileQuotaLedger.seedFromDescriptors(registry)
        val ledger = InMemoryQuotaLedger(seed)
        ledger.put(readyRemote(seed, "openai").copy(paidLocked = true)) // locked

        val paidGate = EmergencyPaidGate(temp.resolve("paid").toFile())
        // Unlock openai via paid gate
        paidGate.unlock("openai", "10m", "emergency unlock")

        val decision = RoutePolicy(
            registry = registry,
            ledger = ledger,
            costPolicy = AtroposCostPolicy.PAID_EMERGENCY_UNLOCKED,
            paidGate = paidGate
        ).decide(ProviderTask(ProviderTaskKind.CHAT_PROMPT, ApiCapability.CHAT, "hello"))

        // openai is selected because it is unlocked by paidGate
        assertEquals("openai", decision.selectedProviderId)
    }

    private fun readyRemote(seed: List<ProviderQuotaRecord>, id: String): ProviderQuotaRecord =
        seed.first { it.providerId == id }.copy(
            configured = true,
            verified = true,
            state = ProviderAvailabilityState.READY
        )

    private fun String.lineContaining(needle: String): String =
        lines().firstOrNull { it.contains(needle) }.orEmpty()
}
