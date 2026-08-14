package atropos.core.provider

/**
 * Provider availability, one state per distinct wait.
 *
 * Source Doc 2 §.300 §7 names seven failure transitions and each implies a
 * different recovery: a rate limit clears in seconds, an exhausted quota at a
 * stated reset, a billing failure never without a human. MODEL_MISSING was the
 * one §7 state absent here, and its absence mattered — a named model going away
 * is not an outage, an alternate model may work, and collapsing it into OFFLINE
 * drops a healthy provider out of every chain it appears in.
 */
enum class ProviderAvailabilityState {
    READY, COOLDOWN, EXHAUSTED_UNTIL_RESET, AUTH_FAILED, BILLING_REQUIRED, OFFLINE, DEGRADED, UNKNOWN, DISABLED,

    /** The named model is gone; §7 says try an alternate model before falling back. */
    MODEL_MISSING;

    /** True when only a human can return this provider to service. */
    val needsHuman: Boolean get() = this == AUTH_FAILED || this == BILLING_REQUIRED

    /** True when no elapsed time alone permits another attempt. */
    val neverAutoRetries: Boolean get() = needsHuman || this == DISABLED
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
