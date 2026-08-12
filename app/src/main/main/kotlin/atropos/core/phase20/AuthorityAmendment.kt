/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.phase20

import java.time.Instant

/**
 * An accepted proposal, recorded as new authority.
 *
 * §20.8: "Accepted proposals become versioned authority amendments with
 * independent hashes." The independence is the point — the amendment gets its
 * own hash and the original Source Doc hash is untouched, so a reader can always
 * tell what the documents originally said and what was added later. §20.1 makes
 * that immutability absolute: original authority is never edited, only
 * superseded by something that names it.
 *
 * [supersedes] is therefore a reference, never a replacement. Nothing in this
 * type can express "the original now reads differently".
 */
data class AuthorityAmendment(
    val id: String,
    val proposalId: String,
    /** This amendment's own content hash, distinct from any source document's. */
    val sha256: String,
    /** The authority hash this amends, left intact. */
    val supersedes: String,
    val acceptedBy: String,
    val acceptedAt: Instant,
    /** §20.19: the hashes a completion claim must cite. */
    val evidenceHashes: List<String>
) {
    fun render(): String =
        "amendment $id sha=$sha256 supersedes=$supersedes accepted_by=$acceptedBy " +
            "evidence=${evidenceHashes.size}"
}

/**
 * The observation period a promoted change must survive.
 *
 * §20.14 requires it and §20.8/`P20-H04` make it a state rather than a comment:
 * "after promotion, observe before next change in same subsystem". Without a
 * durable timer the rule is unenforceable, and oscillating changes to one
 * subsystem look like progress.
 */
data class ObservationPeriod(
    val subsystem: String,
    val startedAt: Instant,
    val durationSeconds: Long
) {
    fun isOpenAt(now: Instant): Boolean =
        now.isBefore(startedAt.plusSeconds(durationSeconds))

    fun remainingSecondsAt(now: Instant): Long =
        (startedAt.plusSeconds(durationSeconds).epochSecond - now.epochSecond).coerceAtLeast(0)
}
