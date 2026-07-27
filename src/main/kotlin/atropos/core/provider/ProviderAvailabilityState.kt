package atropos.core.provider

enum class ProviderAvailabilityState {
    READY, COOLDOWN, EXHAUSTED_UNTIL_RESET, AUTH_FAILED, BILLING_REQUIRED, OFFLINE, DEGRADED, UNKNOWN, DISABLED
}

data class ProviderHealth(
    val providerId: String,
    val state: ProviderAvailabilityState,
    val verified: Boolean = false,
    val activeModel: String? = null,
    val cooldownUntilEpochMs: Long? = null,
    val resetAtEpochMs: Long? = null,
    val lastErrorClass: String? = null,
    val lastErrorSummary: String? = null,
    val latencyMsAvg: Long? = null,
    val successScore: Double = 0.0
)
