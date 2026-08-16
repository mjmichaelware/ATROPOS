/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.storage

data class WatermarkDecision(val allowed: Boolean, val emergency: Boolean, val reason: String)

class LocalWatermarkGuard(
    private val warningFraction: Double = 0.85,
    private val refusalFraction: Double = 0.95
) {
    init { require(warningFraction in 0.0..1.0 && refusalFraction >= warningFraction) }

    fun evaluate(usedBytes: Long, ceilingBytes: Long): WatermarkDecision {
        if (ceilingBytes <= 0) return WatermarkDecision(false, true, "storage ceiling is not positive")
        val fraction = usedBytes.toDouble() / ceilingBytes
        return when {
            fraction >= refusalFraction -> WatermarkDecision(false, true, "local storage refusal watermark reached")
            fraction >= warningFraction -> WatermarkDecision(true, false, "local storage warning watermark reached")
            else -> WatermarkDecision(true, false, "local storage below warning watermark")
        }
    }
}
