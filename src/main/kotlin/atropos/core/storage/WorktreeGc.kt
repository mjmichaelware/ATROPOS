/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.storage

import atropos.core.worktree.WorktreeRecord
import atropos.core.worktree.WorktreeRecordStore
import java.time.Instant

/**
 * Reclaims worktrees that no run still needs.
 *
 * `SUP.STOR.WORKTREE-GC`: "Abandoned worktrees cannot accumulate indefinitely;
 * storage reclaim is deterministic and evidence-preserving. Competitors leave
 * worktrees forever."
 *
 * The safety rule from the atom is absolute: "never delete a worktree
 * referenced by an open GoalRun or unpushed evidence". Two things follow.
 *
 * A worktree that is neither merged nor rolled back is *unresolved*, not
 * abandoned. It holds the only copy of work that was attempted, and the fact
 * that nobody has looked at it recently is not evidence that nobody will —
 * that is precisely the state a crashed run leaves behind. Age alone never
 * makes it collectable.
 *
 * Whether a run is open is asked of the caller through [openRunIds]. Reaching
 * into the run store from here would tie collection to that store's shape, and
 * the collector would then have to be edited every time the run model changed
 * — which is how a safety check quietly stops matching reality.
 */
class WorktreeGc(
    private val store: WorktreeRecordStore,
    private val reclaimer: StorageReclaimer,
    private val policy: RetentionPolicy = RetentionPolicy()
) {
    /**
     * @param openRunIds job ids with work still in flight. Anything they
     *   reference is held regardless of age or pressure.
     * @param dryRun default true. Deletion happens when it is asked for.
     */
    fun collect(
        openRunIds: Set<String> = emptySet(),
        pressure: Double = 0.0,
        dryRun: Boolean = true,
        now: Instant = Instant.now()
    ): GcOutcome {
        val removed = mutableListOf<GcCandidate>()
        val retained = mutableListOf<GcRetention>()
        val failed = mutableListOf<String>()

        for (record in store.list()) {
            val bytes = reclaimer.sizeOf(record.worktreePath)

            val holder = heldBy(record, openRunIds)
            if (holder != null) {
                retained += GcRetention(record.id, bytes, holder)
                continue
            }

            val tier = policy.tierFor(
                storageClass = STORAGE_CLASS,
                age = ageOf(record.updatedAt, now),
                referenced = false,
                pressure = pressure
            )
            if (tier != RetentionTier.DELETE) {
                retained += GcRetention(record.id, bytes, "still ${tier.canonical}: ${tier.description}")
                continue
            }

            val candidate = GcCandidate(
                id = record.id,
                path = record.worktreePath,
                storageClass = STORAGE_CLASS,
                bytes = bytes,
                lastUsed = record.updatedAt,
                tier = tier,
                reason = resolution(record) + ", past the ${policy.ruleFor(STORAGE_CLASS).coldFor.toDays()}-day window"
            )

            if (dryRun) {
                removed += candidate
                continue
            }

            val freed = reclaimer.remove(record.worktreePath)
            if (freed == null) {
                failed += "${record.id}: ${record.worktreePath} could not be removed"
                continue
            }
            // The meta file goes only after the directory did. A record whose
            // worktree survived would be invisible to the next collection and
            // the directory would then never be reclaimed at all.
            store.delete(record)
            removed += candidate.copy(bytes = freed)
        }

        return GcOutcome(STORAGE_CLASS, removed, retained, failed, dryRun)
    }

    /** What is holding this worktree, or null when nothing is. */
    private fun heldBy(record: WorktreeRecord, openRunIds: Set<String>): String? = when {
        record.jobId in openRunIds -> "job ${record.jobId} is still open"

        !record.mergedBack && !record.rolledBack ->
            "unresolved: neither merged nor rolled back, so it holds the only copy of this attempt"

        record.verified && !record.mergedBack ->
            "verified but not merged; the evidence has not reached the tree"

        else -> null
    }

    private fun resolution(record: WorktreeRecord): String =
        if (record.mergedBack) "merged back" else "rolled back"

    private companion object {
        const val STORAGE_CLASS = "worktrees"
    }
}
