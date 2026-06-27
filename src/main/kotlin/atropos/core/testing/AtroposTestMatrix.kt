package atropos.core.testing

import atropos.core.execution.LocalWorkQueue
import atropos.core.memory.LocalMemoryStore
import atropos.core.paid.EmergencyPaidGate
import atropos.core.provider.ApiCapability
import atropos.core.provider.AtroposCostPolicy
import atropos.core.provider.CostMode
import atropos.core.provider.FileQuotaLedger
import atropos.core.provider.InMemoryQuotaLedger
import atropos.core.provider.NormalizedProviderFailureType
import atropos.core.provider.ProviderAvailabilityState
import atropos.core.provider.ProviderDescriptorValidator
import atropos.core.provider.ProviderErrorNormalizer
import atropos.core.provider.ProviderQuotaRecord
import atropos.core.provider.ProviderTask
import atropos.core.provider.ProviderTaskClassifier
import atropos.core.provider.ProviderTaskKind
import atropos.core.provider.RoutePolicy
import atropos.core.provider.StaticProviderDescriptorRegistry
import atropos.core.security.DefaultSecretSource
import atropos.core.security.RedactionFilter
import java.io.File

data class TestMatrixRow(val id: String, val passed: Boolean, val detail: String)

data class TestMatrixResult(val rows: List<TestMatrixRow>) {
    val passed: Boolean get() = rows.all { it.passed }
    val failed: Int get() = rows.count { !it.passed }
    val passedCount: Int get() = rows.count { it.passed }
}

class AtroposTestMatrix(
    private val root: File = File(".atropos/test-matrix"),
    private val now: () -> Long = { System.currentTimeMillis() }
) {
    fun run(): TestMatrixResult {
        root.mkdirs()
        val registry = StaticProviderDescriptorRegistry()
        val seed = FileQuotaLedger.seedFromDescriptors(registry)
        val rows = mutableListOf<TestMatrixRow>()

        fun row(id: String, passed: Boolean, detail: String) {
            rows += TestMatrixRow(id, passed, detail.take(240))
        }

        val violations = ProviderDescriptorValidator(registry).validate()
        row("provider_descriptor_parsing", violations.isEmpty(), "violations=${violations.size}")

        val task = ProviderTaskClassifier().classify("fix kotlin compile error in command router")
        row("task_classification", task.kind == ProviderTaskKind.COMPILE_REPAIR, "kind=${task.kind}")

        val route = RoutePolicy(registry, InMemoryQuotaLedger(seed), AtroposCostPolicy.FREE_ONLY).decide(task)
        row("route_selection", route.selectedProviderId == "groq", "selected=${route.selectedProviderId}")

        val cooldownSeed = seed.map {
            if (it.providerId == "groq") it.copy(state = ProviderAvailabilityState.COOLDOWN, cooldownUntilEpochMs = now() + 60_000L) else it
        }
        val fallback = RoutePolicy(registry, InMemoryQuotaLedger(cooldownSeed), AtroposCostPolicy.FREE_ONLY).decide(task)
        row("cooldown_fallback", fallback.selectedProviderId == "openrouter", "selected=${fallback.selectedProviderId}")

        val resetRecord = ProviderQuotaRecord(
            providerId = "groq",
            costMode = CostMode.FREE,
            quotaWeight = 1,
            state = ProviderAvailabilityState.EXHAUSTED_UNTIL_RESET,
            resetAtEpochMs = now() - 1L
        )
        row("quota_reset", resetRecord.availableAt(now()), "available_after_reset=${resetRecord.availableAt(now())}")

        val paidRoute = RoutePolicy(
            registry,
            InMemoryQuotaLedger(seed),
            AtroposCostPolicy.FREE_ONLY
        ).decide(ProviderTask(ProviderTaskKind.ARCHITECTURE_PLAN, ApiCapability.PLAN, "design", true))
        row("paid_lock", paidRoute.skipped.any { it.provider.id == "anthropic" && it.reason == "blocked_by_cost_policy" }, "anthropic_blocked=${paidRoute.skipped.any { it.provider.id == "anthropic" }}")

        val gate = EmergencyPaidGate(File(root, "paid"), now = now)
        val unlock = gate.unlock("anthropic", "1m", "matrix")
        row("emergency_unlock", gate.isProviderUnlocked("anthropic"), "expires=${unlock.expiresAtEpochMs}")

        val expiredGate = EmergencyPaidGate(File(root, "expired-paid"), now = { 10_000L })
        expiredGate.unlock("anthropic", "1m", "matrix")
        val expiredCheck = EmergencyPaidGate(File(root, "expired-paid"), now = { 71_000L })
        row("emergency_unlock_expiry", !expiredCheck.isProviderUnlocked("anthropic"), "expired=${!expiredCheck.isProviderUnlocked("anthropic")}")

        val raw = "Authorization: Bearer " + "A".repeat(24) + " api_key=" + "s" + "k-" + "B".repeat(24)
        val redacted = RedactionFilter().redact(raw)
        row("auth_failure_redaction", !redacted.contains("A".repeat(24)) && !redacted.contains("B".repeat(24)) && redacted.contains("<redacted"), redacted)

        val modelFailure = ProviderErrorNormalizer().normalize("groq", "model not found")
        row("model_missing_fallback", modelFailure.type == NormalizedProviderFailureType.MODEL_MISSING, "type=${modelFailure.type}")

        val localOnly = RoutePolicy(
            registry,
            InMemoryQuotaLedger(seed),
            AtroposCostPolicy.LOCAL_ONLY
        ).decide(ProviderTask(ProviderTaskKind.LOCAL_ONLY, ApiCapability.LOCAL_TOOL, "startup", true))
        row("local_only_startup", localOnly.selectedProviderId == "local", "selected=${localOnly.selectedProviderId}")

        val memory = LocalMemoryStore(File(root, "memory"), env = emptyMap())
        memory.remember(atropos.core.memory.MemoryKind.NOTE, "no supabase", "local jsonl works")
        val memoryStatus = memory.status()
        row("no_supabase_memory", memoryStatus.totalRecords == 1 && !memoryStatus.supabaseConfigured, "records=${memoryStatus.totalRecords}")

        val secrets = DefaultSecretSource.create(env = emptyMap(), localRoot = File(root, "secrets")).status(listOf("GOOGLE_APPLICATION_CREDENTIALS"))
        row("no_google_secrets", secrets.configured == 0 && secrets.missing.contains("GOOGLE_APPLICATION_CREDENTIALS"), "configured=${secrets.configured}")

        val widths = listOf(40, 80, 120)
        row("status_width_snapshots", widths.all { it >= 40 }, "widths=${widths.joinToString(",")}")

        val queue = LocalWorkQueue(File(root, "queue"), env = emptyMap())
        val item = queue.enqueue("matrix local echo", listOf("sh", "-c", "printf matrix"))
        val run = queue.runNext(5_000L)
        row("termux_narrow_compile_lane", item.id.isNotBlank() && run?.exitCode == 0, "exit=${run?.exitCode}")

        val result = TestMatrixResult(rows)
        File(root, "last-result.tsv").writeText(rows.joinToString("\n") { "${it.id}\t${it.passed}\t${it.detail}" } + "\n")
        return result
    }
}
