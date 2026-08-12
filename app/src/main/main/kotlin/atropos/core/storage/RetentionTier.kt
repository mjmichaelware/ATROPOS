/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.storage

/**
 * The four retention tiers every stored class is assigned to.
 *
 * `SUP.STOR.RETENTION-TIERS` requires storage behaviour to be declarative:
 * "no hidden accumulation". Ad-hoc deletion scattered across services is how a
 * phone-first system fills its disk over days of autonomy — each site is
 * individually reasonable and nothing owns the total.
 *
 * Tiers are ordered by how reclaimable they are, so a garbage collector can
 * walk them in order and stop as soon as it has freed enough. `HOT` is never
 * reclaimable: it is the run currently executing, and reclaiming it would
 * destroy the evidence of the thing in progress.
 */
enum class RetentionTier(
    val canonical: String,
    val description: String,
    val reclaimable: Boolean
) {
    HOT("hot", "belongs to an active run; never reclaimed", reclaimable = false),
    WARM("warm", "recent runs kept for inspection", reclaimable = true),
    COLD("cold", "archived by hash; content may be dropped", reclaimable = true),
    DELETE("delete", "already eligible for removal", reclaimable = true);

    companion object {
        /** Reclaim order: cheapest loss first. */
        val RECLAIM_ORDER: List<RetentionTier> = listOf(DELETE, COLD, WARM)
    }
}

/** A class of stored data and the tier that governs it. */
data class StorageClass(
    val id: String,
    val tier: RetentionTier,
    /** Bytes currently attributed to this class. */
    val bytes: Long
)
