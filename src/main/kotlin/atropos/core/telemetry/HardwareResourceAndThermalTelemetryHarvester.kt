/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.telemetry

import atropos.core.platform.PlatformWire
import java.lang.management.ManagementFactory
import kotlin.math.max

/**
 * SD7-T1-TEL17: Hardware Resource & Thermal Telemetry Harvester.
 *
 * Implements physics-based execution energy integration:
 * E = P_idle * delta_t + alpha * (cpu_ms / wall_ms) * delta_t + beta * RSS_bytes.
 * Incorporates PlatformWire thermal status and architecture scaling.
 */
class HardwareResourceAndThermalTelemetryHarvester(
    private val platformWire: PlatformWire = PlatformWire(),
    private val ringBuffer: TelemetryRingBuffer? = null
) {
    companion object {
        const val P_IDLE_WATTS: Double = 3.5
        const val ALPHA_CPU_ACTIVE_WATTS: Double = 25.0
        const val BETA_MEMORY_JOULES_PER_BYTE: Double = 4.0e-9
        const val THERMAL_THROTTLE_DERATING_FACTOR: Double = 0.65
    }

    /**
     * Functional calculation of energy expenditure (in Joules) from hardware telemetry.
     */
    fun computeEnergyJoules(
        durationMs: Long,
        cpuMonotonicMs: Long,
        rssBytes: Long,
        isThrottled: Boolean
    ): Double {
        val seconds = max(1L, durationMs) / 1000.0
        val cpuUtilization = (cpuMonotonicMs.toDouble() / max(1L, durationMs).toDouble()).coerceIn(0.0, 1.0)
        val thermalMultiplier = if (isThrottled) THERMAL_THROTTLE_DERATING_FACTOR else 1.0

        val powerWatts = (P_IDLE_WATTS + (ALPHA_CPU_ACTIVE_WATTS * cpuUtilization * thermalMultiplier))
        val dynamicEnergy = powerWatts * seconds
        val memoryEnergy = rssBytes.toDouble() * BETA_MEMORY_JOULES_PER_BYTE

        return dynamicEnergy + memoryEnergy
    }

    fun measureAndRecord(
        instanceId: String,
        durationMs: Long,
        operationKind: String = "step"
    ): ResourceGasExpenditureRecord {
        val memoryBean = ManagementFactory.getMemoryMXBean()
        val heapUsage = memoryBean.heapMemoryUsage.used
        val nonHeapUsage = memoryBean.nonHeapMemoryUsage.used
        val totalRssBytes = max(0L, heapUsage + nonHeapUsage)

        val arch = System.getProperty("os.arch") ?: "unknown"
        val thermalPacing = platformWire.evaluateThermalPacing()
        val isThrottled = thermalPacing.throttled
        val thermalStatus = if (isThrottled) "throttled" else "nominal"

        val estimatedJoules = computeEnergyJoules(
            durationMs = durationMs,
            cpuMonotonicMs = durationMs,
            rssBytes = totalRssBytes,
            isThrottled = isThrottled
        )

        val payload = ResourceGasExpenditureRecord(
            cpuMonotonicMs = durationMs,
            peakRssBytes = totalRssBytes,
            estimatedJoules = estimatedJoules,
            thermalState = thermalStatus,
            hostArch = arch
        )

        val record = FrontierSweTelemetryRecord.create(
            instanceId = instanceId,
            h0PromptLineage = "hw-$operationKind-$arch",
            payload = payload,
            rewardSignal = 0.0
        )
        ringBuffer?.offer(record)

        return payload
    }
}
