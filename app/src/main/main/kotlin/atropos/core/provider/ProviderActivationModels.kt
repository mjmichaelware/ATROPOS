package atropos.core.provider

import atropos.core.provider.adapter.AdapterStatus
import atropos.core.security.RedactionFilter
import java.time.Instant

enum class ProviderActivationState {
    MISSING,
    CONFIGURED,
    FIXTURE_BACKED,
    DRY_RUN_CAPABLE,
    VERIFIED,
    INVALID_KEY,
    AUTH_FAILED,
    RATE_LIMITED,
    QUOTA_EXHAUSTED,
    BILLING_REQUIRED,
    OFFLINE,
    DEGRADED,
    LOCKED,
    DISABLED,
    READY
}

enum class ProviderVerificationMode {
    SNAPSHOT,
    VERIFY,
    LIVE_TEST
}

data class ProviderFixtureMatrixRecord(
    val providerId: String,
    val passed: Boolean,
    val passedCount: Int,
    val totalCount: Int,
    val details: List<String>
) {
    fun summary(): String = "$passedCount/$totalCount"
}

data class ProviderActivationRecord(
    val providerId: String,
    val mode: ProviderVerificationMode,
    val state: ProviderActivationState,
    val descriptorPresent: Boolean,
    val adapterStatus: AdapterStatus?,
    val keySources: List<String>,
    val impact: List<String>,
    val executableSupport: Boolean,
    val fixtureMatrix: ProviderFixtureMatrixRecord?,
    val verificationSummary: String,
    val remediation: String,
    val lastCheckedAt: Instant = Instant.now(),
    val lastUsedAt: Instant? = null,
    val routeEligibility: List<String> = emptyList(),
    val quotaCooldownUntil: Instant? = null
) {
    fun render(): String = RedactionFilter().redact(buildString {
        appendLine("provider: $providerId")
        appendLine("  mode: ${mode.name.lowercase()}")
        appendLine("  state: ${state.name.lowercase()}")
        appendLine("  descriptor: ${yesNo(descriptorPresent)}")
        appendLine("  adapter: ${adapterStatus?.providerId ?: "none"}")
        adapterStatus?.let { status ->
            appendLine("  adapter implemented: ${yesNo(status.implemented)}")
            appendLine("  adapter configured: ${yesNo(status.configured)}")
            appendLine("  dry-run only: ${yesNo(status.dryRunOnly)}")
            appendLine("  adapter health: ${status.health}")
            appendLine("  adapter detail: ${status.detail}")
        }
        appendLine("  key sources: ${keySources.joinToString(",").ifBlank { "none" }}")
        appendLine("  impact: ${impact.joinToString(",").ifBlank { "none" }}")
        appendLine("  executable: ${yesNo(executableSupport)}")
        fixtureMatrix?.let {
            appendLine("  fixtures: ${it.summary()} ${if (it.passed) "PASS" else "FAIL"}")
            it.details.forEach { line -> appendLine("    $line") }
        }
        appendLine("  verification: $verificationSummary")
        appendLine("  remediation: $remediation")
        appendLine("  checked at: $lastCheckedAt")
        lastUsedAt?.let { appendLine("  last used at: $it") }
        if (routeEligibility.isNotEmpty()) {
            appendLine("  route eligibility: ${routeEligibility.joinToString(",")}")
        }
        quotaCooldownUntil?.let { appendLine("  quota cooldown until: $it") }
    }.trimEnd())

    companion object {
        private fun yesNo(value: Boolean): String = if (value) "yes" else "no"
    }
}
