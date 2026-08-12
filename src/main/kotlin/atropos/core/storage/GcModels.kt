/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.storage

import java.nio.file.Path
import java.time.Instant

/**
 * One thing a collector could remove, and why it may.
 *
 * `SUP.STOR.WORKTREE-GC`: "Log every deletion with reason." A reclaim that
 * cannot say what it took and on what grounds is indistinguishable from data
 * loss when the operator goes looking for the run that explains a failure.
 */
data class GcCandidate(
    val id: String,
    val path: Path,
    val storageClass: String,
    val bytes: Long,
    val lastUsed: Instant,
    val tier: RetentionTier,
    /** Why this may be removed, in the operator's terms. */
    val reason: String
)

/**
 * One thing a collector must not remove, and what is holding it.
 *
 * Retained items are reported, not silently skipped. An operator running a
 * collector under storage pressure needs to know that nothing was freed
 * *because* four runs are still open — otherwise the collector looks broken
 * at exactly the moment it is behaving correctly.
 */
data class GcRetention(val id: String, val bytes: Long, val heldBy: String)

/**
 * @param dryRun true when nothing was actually removed. The default everywhere
 *   a collector is invoked automatically: deletion is the one storage action
 *   that cannot be undone, so it happens when asked for, not as a side effect
 *   of measuring.
 */
data class GcOutcome(
    val storageClass: String,
    val removed: List<GcCandidate>,
    val retained: List<GcRetention>,
    val failed: List<String>,
    val dryRun: Boolean
) {
    val reclaimedBytes: Long get() = removed.sumOf { it.bytes }

    fun render(): String = buildString {
        append(if (dryRun) "would reclaim" else "reclaimed")
        append(" ${reclaimedBytes}B from $storageClass")
        append(" (${removed.size} removed, ${retained.size} held")
        if (failed.isNotEmpty()) append(", ${failed.size} failed")
        append(")")
    }

    /** The deletion log the atom requires: one line per item, with its reason. */
    fun auditLines(): List<String> =
        removed.map { "${if (dryRun) "would-remove" else "removed"} ${it.id} ${it.bytes}B — ${it.reason}" } +
            retained.map { "held ${it.id} ${it.bytes}B — ${it.heldBy}" } +
            failed.map { "failed $it" }
}
