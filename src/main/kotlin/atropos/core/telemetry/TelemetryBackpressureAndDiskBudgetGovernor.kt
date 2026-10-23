/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.telemetry

import atropos.core.storage.StorageSupervisor
import java.io.File
import kotlin.math.exp
import kotlin.random.Random

/**
 * SD7-T1-TEL19: Telemetry Backpressure and Disk Budget Governor.
 *
 * Implements mathematical dynamic-capacity backpressure:
 * 1. Mobile (100 MB) and Desktop (1 GB) spool budgets.
 * 2. Continuous exponential admission probability P_accept = exp(-kappa * delta_ratio)
 *    between soft watermark (80%) and hard watermark (95%).
 * 3. High-value negative prioritization (P_accept = 1.0 even under backpressure).
 * 4. StorageSupervisor integration for hard capacity boundaries.
 */
class TelemetryBackpressureAndDiskBudgetGovernor(
    private val spoolDir: File,
    private val isMobilePlatform: Boolean = false,
    private val storageSupervisor: StorageSupervisor? = null
) {
    companion object {
        const val MOBILE_BUDGET_BYTES: Long = 100L * 1024L * 1024L  // 100 MB
        const val DESKTOP_BUDGET_BYTES: Long = 1024L * 1024L * 1024L // 1 GB
        const val SOFT_WATERMARK_RATIO: Double = 0.80
        const val HARD_WATERMARK_RATIO: Double = 0.95
        const val EXPONENTIAL_DECAY_KAPPA: Double = 5.0
    }

    private val maxBudget: Long = if (isMobilePlatform) MOBILE_BUDGET_BYTES else DESKTOP_BUDGET_BYTES

    fun currentSpoolSizeBytes(): Long {
        if (!spoolDir.exists()) return 0L
        return spoolDir.walkTopDown().filter { it.isFile }.map { it.length() }.sum()
    }

    /**
     * Functional calculation of admission probability under backpressure.
     */
    fun computeAdmissionProbability(currentBytes: Long, isHighValueNegative: Boolean): Double {
        val utilizationRatio = currentBytes.toDouble() / maxBudget.toDouble()
        if (utilizationRatio < SOFT_WATERMARK_RATIO) {
            return 1.0
        }
        if (isHighValueNegative) {
            return if (utilizationRatio < HARD_WATERMARK_RATIO) 1.0 else 0.5
        }
        if (utilizationRatio >= HARD_WATERMARK_RATIO) {
            return 0.0
        }
        val normalizedExcess = (utilizationRatio - SOFT_WATERMARK_RATIO) / (HARD_WATERMARK_RATIO - SOFT_WATERMARK_RATIO)
        return exp(-EXPONENTIAL_DECAY_KAPPA * normalizedExcess)
    }

    fun shouldAcceptRecord(record: FrontierSweTelemetryRecord): Boolean {
        val current = currentSpoolSizeBytes()
        val isHighValueNegative = record.rewardSignal <= -1.0 ||
                (record.kind == TelemetryKind.MUTATION_KILL_RATIO && record.rewardSignal < 0.0)

        // Check StorageSupervisor preflight
        if (storageSupervisor != null && !storageSupervisor.canAdmitPreflight(1024L)) {
            return isHighValueNegative
        }

        val pAccept = computeAdmissionProbability(current, isHighValueNegative)
        if (pAccept >= 1.0) return true
        if (pAccept <= 0.0) return false
        return Random.nextDouble() < pAccept
    }

    fun isUnderBackpressure(): Boolean {
        val ratio = currentSpoolSizeBytes().toDouble() / maxBudget.toDouble()
        return ratio >= SOFT_WATERMARK_RATIO
    }
}
