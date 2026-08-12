/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.storage

/**
 * The single place a storage-growing action can be refused.
 *
 * `SUP.STOR.FREE-SPACE-GATE` sets the bands — "warn at 15 %, refuse new GoalRun
 * at 8 %, emergency GC at 5 %" — and the predicate they serve:
 * `P(disk-full-crash)=0` under continuous operation. A system that only
 * discovers it is full at the moment of writing has already corrupted whatever
 * it was half-way through writing.
 *
 * Every refusal names what could be reclaimed. A gate that says only "no" makes
 * the operator guess; §4.1 requires a failure to state what to do about it.
 */
class FreeSpaceGate(
    private val warnFraction: Double = 0.85,
    private val refuseFraction: Double = 0.92,
    private val emergencyFraction: Double = 0.95
) {
    fun evaluate(constitution: StorageConstitution, requestedBytes: Long): FreeSpaceDecision {
        if (requestedBytes < 0) {
            return FreeSpaceDecision.Refused(
                "a negative allocation is not a request",
                reclaimableBytes = constitution.reclaimableBytes()
            )
        }

        val projected = if (constitution.ceilingBytes <= 0) 1.0
        else (constitution.usedBytes + requestedBytes).toDouble() / constitution.ceilingBytes.toDouble()

        return when {
            projected >= emergencyFraction -> FreeSpaceDecision.Refused(
                "storage is at ${pct(projected)}% of the declared ceiling; emergency reclaim required",
                reclaimableBytes = constitution.reclaimableBytes(),
                emergency = true
            )
            projected >= refuseFraction -> FreeSpaceDecision.Refused(
                "storage would reach ${pct(projected)}% of the declared ceiling",
                reclaimableBytes = constitution.reclaimableBytes()
            )
            projected >= warnFraction -> FreeSpaceDecision.AllowedWithWarning(
                "storage will be at ${pct(projected)}% of the declared ceiling after this"
            )
            else -> FreeSpaceDecision.Allowed
        }
    }

    private fun pct(fraction: Double): Int = (fraction * 100).toInt()
}

sealed class FreeSpaceDecision {
    object Allowed : FreeSpaceDecision()
    data class AllowedWithWarning(val warning: String) : FreeSpaceDecision()
    data class Refused(
        val reason: String,
        /** What the operator could free instead of guessing. */
        val reclaimableBytes: Long,
        val emergency: Boolean = false
    ) : FreeSpaceDecision()

    val permitted: Boolean get() = this !is Refused
}
