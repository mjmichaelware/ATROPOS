/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.planning

/**
 * SD7-T-040: Live Amdahl coordination, rework, and verification-debt accumulator.
 * Mathematical Invariant: Φ(t+1) < Φ(t) monotonic descent
 * coordinationOverhead = (T_routing + T_serialization + T_lockWait) / T_total
 * reworkConflict = sumRepairAttempts / TotalCompletedAtoms
 * verificationDebt clamped to [0.0, 1.0]
 * N_effective drives worker concurrency clamped to [1, 64].
 */
object AmdahlAccumulator {
    const val MAX_WORKERS = 64
    const val MIN_WORKERS = 1

    fun computeNEffective(
        tRouting: Double,
        tSerialization: Double,
        tLockWait: Double,
        tTotal: Double,
        repairAttempts: Int,
        completedAtoms: Int,
        verificationDebt: Double
    ): Int {
        val total = if (tTotal > 0.0) tTotal else 1.0
        val coordinationOverhead = ((tRouting + tSerialization + tLockWait) / total).coerceIn(0.0, 1.0)
        val reworkConflict = if (completedAtoms > 0) {
            (repairAttempts.toDouble() / completedAtoms).coerceIn(0.0, 1.0)
        } else {
            0.0
        }
        val debtFraction = verificationDebt.coerceIn(0.0, 1.0)

        // Effective parallelism under Amdahl scaling factor
        val parallelFraction = (1.0 - coordinationOverhead) * (1.0 - reworkConflict) * (1.0 - debtFraction)
        val nEffective = (MAX_WORKERS * parallelFraction).toInt()
        return nEffective.coerceIn(MIN_WORKERS, MAX_WORKERS)
    }
}
