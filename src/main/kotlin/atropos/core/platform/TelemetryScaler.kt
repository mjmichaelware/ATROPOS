/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.platform

/**
 * SD7-T-120: Dynamic host telemetry concurrency scaler.
 * Mathematical Invariant: Φ(t+1) < Φ(t) monotonic descent
 * Parallel workers coerced to [1, 64] based on battery < 20% or temperature > 42°C.
 */
class TelemetryScaler {
    companion object {
        const val MAX_WORKERS = 64
        const val MIN_WORKERS = 1
        const val THROTTLED_WORKERS = 2
        const val CRITICAL_BATTERY_PERCENT = 20.0
        const val CRITICAL_TEMPERATURE_CELSIUS = 42.0
    }

    fun scaleWorkers(temp: Double, battery: Double): Int {
        if (battery < CRITICAL_BATTERY_PERCENT || temp > CRITICAL_TEMPERATURE_CELSIUS) {
            return THROTTLED_WORKERS.coerceIn(MIN_WORKERS, MAX_WORKERS)
        }
        val thermalHeadroom = ((50.0 - temp) / 20.0).coerceIn(0.1, 1.0)
        val batteryHeadroom = (battery / 100.0).coerceIn(0.1, 1.0)
        val scaling = thermalHeadroom * batteryHeadroom
        return (MAX_WORKERS * scaling).toInt().coerceIn(MIN_WORKERS, MAX_WORKERS)
    }
}
