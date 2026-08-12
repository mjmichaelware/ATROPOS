/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.storage

/**
 * The declared limits on local storage, and what is currently using it.
 *
 * `SUP.STOR.GLOBAL-BYTE-CEILING`: "Local storage is a hard, visible resource
 * rather than an unbounded side effect; phone remains usable after days of
 * autonomy." Competitors treat disk as infinite, which is survivable on a
 * workstation and is not on the device this system targets.
 *
 * The constitution decides nothing on its own — it reports. Refusal belongs to
 * [FreeSpaceGate], which is the single place an allocation can be stopped.
 */
data class StorageConstitution(
    /** The operator's declared ceiling for everything ATROPOS stores locally. */
    val ceilingBytes: Long,
    val classes: List<StorageClass> = emptyList()
) {
    val usedBytes: Long get() = classes.sumOf { it.bytes }

    val remainingBytes: Long get() = (ceilingBytes - usedBytes).coerceAtLeast(0)

    val fractionUsed: Double
        get() = if (ceilingBytes <= 0) 1.0 else usedBytes.toDouble() / ceilingBytes.toDouble()

    /** True when a write of this size would exceed the declared ceiling. */
    fun wouldExceed(bytes: Long): Boolean = usedBytes + bytes > ceilingBytes

    /**
     * What could be freed, in reclaim order.
     *
     * `HOT` never appears. A collector that could reclaim the active run's
     * evidence would destroy the record of the thing currently executing, which
     * is the one record that cannot be regenerated.
     */
    fun reclaimable(): List<StorageClass> =
        RetentionTier.RECLAIM_ORDER.flatMap { tier -> classes.filter { it.tier == tier } }

    fun reclaimableBytes(): Long = reclaimable().sumOf { it.bytes }

    fun render(): String =
        "storage used=$usedBytes ceiling=$ceilingBytes used%=${(fractionUsed * 100).toInt()} " +
            "reclaimable=${reclaimableBytes()}"
}
