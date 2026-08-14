/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.provider

/**
 * Turns a normalized provider failure into the availability transition Source
 * Doc 2 §.300 §7 specifies for it.
 *
 * [ProviderErrorNormalizer] already answers *what went wrong* and produces a
 * typed [ProviderFailure]. What was missing is §7's other half: *what state that
 * implies, and when the provider may be tried again*. The two are separately
 * wrong in different ways — a normalizer that misreads a message mislabels one
 * failure, while a scheduler that misreads a label retries a declined card
 * forever.
 *
 * > 429/rate limit → state=cooldown, set cooldown_until from headers if
 * > available · quota exhausted → state=exhausted_until_reset, set reset_at ·
 * > billing required → state=billing_required, never auto-retry · auth failed →
 * > state=auth_failed, skip until the key is updated · model missing → try
 * > provider alternate model, then fallback · timeout/offline → short cooldown ·
 * > malformed response → log and try next.
 *
 * Seven outcomes, seven different waits. Collapsing them into "failed, try next"
 * produces two opposite bugs at once — a billing failure retried forever and a
 * rate limit abandoned after one attempt — and both look identical in a log.
 *
 * Operates on [ProviderQuotaRecord], which already owns the runtime fields. No
 * parallel state type: a second record of whether a provider is usable is a
 * second answer to that question, and the two would drift.
 */
class ProviderFailureClassifier(private val nowEpochMs: () -> Long = { System.currentTimeMillis() }) {

    /**
     * Applies a failure to a provider's quota record.
     *
     * @param headers response headers, read opportunistically per Source Doc 2
     *   rule 8. A provider sending `Retry-After` knows better than any local
     *   guess: too short re-triggers the limit and lengthens it, too long idles
     *   a provider that was ready.
     */
    fun classify(
        current: ProviderQuotaRecord,
        failure: ProviderFailure,
        headers: Map<String, String> = emptyMap()
    ): ProviderQuotaRecord {
        val now = nowEpochMs()
        val decayed = (current.successScore * SCORE_DECAY).coerceIn(0.0, 1.0)
        val base = current.copy(
            successScore = decayed,
            lastErrorClass = failure.type.name,
            lastErrorSummary = failure.cleanSummary
        )

        return when (failure.type) {
            NormalizedProviderFailureType.RATE_LIMITED -> base.copy(
                state = ProviderAvailabilityState.COOLDOWN,
                cooldownUntilEpochMs = now + (
                    retryAfterMillis(headers)
                        ?: failure.retryAfterMs
                        ?: RATE_LIMIT_COOLDOWN_MS
                    )
            )

            NormalizedProviderFailureType.QUOTA_EXHAUSTED -> base.copy(
                state = ProviderAvailabilityState.EXHAUSTED_UNTIL_RESET,
                resetAtEpochMs = failure.resetAtEpochMs ?: (now + resetMillis(headers))
            )

            // The one state no elapsed time clears. Setting a cooldown here
            // would let a long-enough wait retry a card that was declined.
            NormalizedProviderFailureType.BILLING_REQUIRED -> base.copy(
                state = ProviderAvailabilityState.BILLING_REQUIRED,
                cooldownUntilEpochMs = null,
                resetAtEpochMs = null,
                paidLocked = true
            )

            NormalizedProviderFailureType.AUTH_FAILED -> base.copy(
                state = ProviderAvailabilityState.AUTH_FAILED,
                cooldownUntilEpochMs = null
            )

            // Not an outage. An alternate model may work, so this stays
            // recoverable and the caller tries one before falling back.
            NormalizedProviderFailureType.MODEL_MISSING -> base.copy(
                state = ProviderAvailabilityState.MODEL_MISSING
            )

            NormalizedProviderFailureType.TIMEOUT,
            NormalizedProviderFailureType.UNAVAILABLE -> base.copy(
                state = ProviderAvailabilityState.COOLDOWN,
                cooldownUntilEpochMs = now + (failure.retryAfterMs ?: TIMEOUT_COOLDOWN_MS)
            )

            // Degraded rather than unusable: one unparseable reply does not mean
            // the next will be, and marking it unusable would drop a healthy
            // provider out of every chain on a single bad response.
            NormalizedProviderFailureType.MALFORMED_RESPONSE,
            NormalizedProviderFailureType.EMPTY_RESPONSE,
            NormalizedProviderFailureType.INTERNAL ->
                base.copy(state = ProviderAvailabilityState.DEGRADED)

            // Not a provider fault at all. The operator stopped it, so nothing
            // about the provider's health changed and its score is untouched.
            NormalizedProviderFailureType.CANCELLED -> current.copy(
                lastErrorClass = failure.type.name,
                lastErrorSummary = failure.cleanSummary
            )
        }
    }

    /** Records a success, clearing transient state and lifting the score. */
    fun succeed(current: ProviderQuotaRecord, latencyMillis: Long): ProviderQuotaRecord =
        current.copy(
            state = ProviderAvailabilityState.READY,
            cooldownUntilEpochMs = null,
            lastErrorClass = null,
            lastErrorSummary = null,
            latencyMsAvg = blend(current.latencyMsAvg, latencyMillis),
            successScore = (current.successScore + (1.0 - current.successScore) * SCORE_RECOVERY)
                .coerceIn(0.0, 1.0)
        )

    /**
     * `Retry-After`, in milliseconds, when the provider sent one.
     *
     * Accepts the delta-seconds form only. The HTTP-date form is legal and
     * essentially unused by these APIs; misparsing it would turn a header
     * meaning "one minute" into a cooldown of decades, so an unrecognised value
     * falls through to the default rather than being guessed at.
     */
    private fun retryAfterMillis(headers: Map<String, String>): Long? {
        val raw = headers.entries.firstOrNull { it.key.equals("retry-after", ignoreCase = true) }?.value
        val seconds = raw?.trim()?.toLongOrNull() ?: return null
        return seconds.coerceIn(1, MAX_COOLDOWN_SECONDS) * 1000
    }

    private fun resetMillis(headers: Map<String, String>): Long {
        val raw = headers.entries
            .firstOrNull { it.key.contains("reset", ignoreCase = true) }?.value?.trim()
        val seconds = raw?.toLongOrNull()
        return if (seconds != null && seconds in 1..MAX_COOLDOWN_SECONDS) seconds * 1000 else DEFAULT_RESET_MS
    }

    /** Exponential blend so one slow call does not redefine the estimate. */
    private fun blend(previous: Long?, observed: Long): Long =
        if (previous == null || previous <= 0) observed else ((previous * 3 + observed) / 4)

    private companion object {
        const val SCORE_DECAY = 0.7
        const val SCORE_RECOVERY = 0.3
        const val MAX_COOLDOWN_SECONDS = 86_400L
        const val RATE_LIMIT_COOLDOWN_MS = 60_000L
        const val TIMEOUT_COOLDOWN_MS = 30_000L
        const val DEFAULT_RESET_MS = 86_400_000L
    }
}
