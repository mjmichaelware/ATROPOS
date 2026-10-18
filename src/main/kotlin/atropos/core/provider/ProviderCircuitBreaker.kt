/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.provider

import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

enum class ProviderHealthState {
    HEALTHY,
    QUARANTINED,
    EXHAUSTED
}

/**
 * Stateful provider circuit breaker and health quarantine.
 * Trips into QUARANTINED on HTTP 401/402 or timed backoff on HTTP 429.
 * Allows ProviderCascadeRouter to filter out unhealthy providers in O(1).
 */
object ProviderCircuitBreaker {
    private const val MAX_STRIKES_BEFORE_EXHAUSTION = 3
    private const val DEFAULT_RATE_LIMIT_QUARANTINE_SEC = 60L

    private val providerStates = ConcurrentHashMap<String, ProviderHealthState>()
    private val quarantineReasons = ConcurrentHashMap<String, String>()
    private val quarantineExpiry = ConcurrentHashMap<String, Long>()
    private val strikeCounts = ConcurrentHashMap<String, AtomicInteger>()

    fun isQuarantined(providerId: String): Boolean {
        val clean = providerId.trim().lowercase(Locale.US)
        val state = providerStates[clean] ?: ProviderHealthState.HEALTHY
        if (state == ProviderHealthState.HEALTHY) return false

        val expiry = quarantineExpiry[clean]
        if (expiry != null && expiry > 0L) {
            val now = System.currentTimeMillis()
            if (now >= expiry) {
                // Timed quarantine has expired: transition back to healthy
                providerStates[clean] = ProviderHealthState.HEALTHY
                quarantineReasons.remove(clean)
                quarantineExpiry.remove(clean)
                strikeCounts.computeIfAbsent(clean) { AtomicInteger(0) }.set(0)
                return false
            }
            return true
        }

        return state == ProviderHealthState.QUARANTINED || state == ProviderHealthState.EXHAUSTED
    }

    fun quarantine(providerId: String, reason: String) {
        val clean = providerId.trim().lowercase(Locale.US)
        providerStates[clean] = ProviderHealthState.QUARANTINED
        quarantineReasons[clean] = reason
        quarantineExpiry.remove(clean)
    }

    fun quarantineUntil(providerId: String, durationSeconds: Long, reason: String) {
        val clean = providerId.trim().lowercase(Locale.US)
        val effectiveSec = if (durationSeconds > 0) durationSeconds else DEFAULT_RATE_LIMIT_QUARANTINE_SEC
        val expiryMs = System.currentTimeMillis() + (effectiveSec * 1000L)
        providerStates[clean] = ProviderHealthState.QUARANTINED
        quarantineReasons[clean] = "$reason (backoff for ${effectiveSec}s)"
        quarantineExpiry[clean] = expiryMs
    }

    fun recordFailure(providerId: String, reason: String, statusCode: Int? = null) {
        val clean = providerId.trim().lowercase(Locale.US)
        when (statusCode) {
            401 -> quarantine(clean, "HTTP 401: unauthorized or invalid credentials")
            402 -> quarantine(clean, "HTTP 402: quota or payment balance exhausted")
            429 -> quarantineUntil(clean, DEFAULT_RATE_LIMIT_QUARANTINE_SEC, "HTTP 429: rate limited")
            else -> {
                val strikes = strikeCounts.computeIfAbsent(clean) { AtomicInteger(0) }.incrementAndGet()
                if (strikes >= MAX_STRIKES_BEFORE_EXHAUSTION) {
                    providerStates[clean] = ProviderHealthState.EXHAUSTED
                    quarantineReasons[clean] = "Exhausted after $strikes consecutive failure strikes ($reason)"
                }
            }
        }
    }

    fun recordSuccess(providerId: String) {
        val clean = providerId.trim().lowercase(Locale.US)
        providerStates[clean] = ProviderHealthState.HEALTHY
        quarantineReasons.remove(clean)
        quarantineExpiry.remove(clean)
        strikeCounts.computeIfAbsent(clean) { AtomicInteger(0) }.set(0)
    }

    fun stateOf(providerId: String): ProviderHealthState {
        val clean = providerId.trim().lowercase(Locale.US)
        if (isQuarantined(clean)) {
            return providerStates[clean] ?: ProviderHealthState.QUARANTINED
        }
        return ProviderHealthState.HEALTHY
    }

    fun quarantineReason(providerId: String): String? =
        quarantineReasons[providerId.trim().lowercase(Locale.US)]

    fun reset() {
        providerStates.clear()
        quarantineReasons.clear()
        quarantineExpiry.clear()
        strikeCounts.clear()
    }
}
