/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.neurosymbolic

/**
 * SD7-T-151: Controlled clock-drift and socket-latency injector.
 * Hard-clamps maximum clock skew and network latency bounds to prevent unbounded skew.
 */
data class ChaosInjectionProfile(
    val maxClockSkewMs: Long = 5_000L,
    val maxSocketLatencyMs: Long = 10_000L,
    val jitterMs: Long = 100L
)

data class ChaosInjectionResult(
    val injectedLatencyMs: Long,
    val injectedClockSkewMs: Long,
    val appliedTarget: String,
    val clamped: Boolean
)

class ClockLatencyChaosInjector(
    private val profile: ChaosInjectionProfile = ChaosInjectionProfile()
) {

    fun inject(target: String): Boolean {
        val result = applyChaos(target, requestedLatencyMs = 250L, requestedSkewMs = 500L)
        return result.injectedLatencyMs >= 0 && result.injectedClockSkewMs >= -profile.maxClockSkewMs
    }

    fun applyChaos(
        target: String,
        requestedLatencyMs: Long,
        requestedSkewMs: Long
    ): ChaosInjectionResult {
        // Enforce clamped bounds against unbounded skew
        val clampedLatency = requestedLatencyMs.coerceIn(0L, profile.maxSocketLatencyMs)
        val clampedSkew = requestedSkewMs.coerceIn(-profile.maxClockSkewMs, profile.maxClockSkewMs)
        val wasClamped = (clampedLatency != requestedLatencyMs) || (clampedSkew != requestedSkewMs)

        return ChaosInjectionResult(
            injectedLatencyMs = clampedLatency,
            injectedClockSkewMs = clampedSkew,
            appliedTarget = target,
            clamped = wasClamped
        )
    }
}
