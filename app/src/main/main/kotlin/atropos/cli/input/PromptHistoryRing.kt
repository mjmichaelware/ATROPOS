/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.input

import atropos.core.security.RedactionFilter

/**
 * Bounded, de-duplicated, redacted recall storage — one ring per lane.
 *
 * This owns retention only: what is kept, in what order, and what is dropped
 * when the ring is full. Traversal is a separate concern and lives in
 * [PromptHistoryBrowser], because the two change for different reasons — a
 * retention rule changes when the memory budget or the redaction policy
 * changes, a traversal rule changes when key handling changes.
 *
 * ## Redaction happens on the way in, not on the way out
 *
 * A prompt line can carry a pasted token. Recalling it later would put the
 * secret back on screen and back into the next request, so the value is
 * redacted before it is stored and the raw text is never retained. Redacting on
 * read instead would leave the plaintext sitting in memory for the lifetime of
 * the session, which is the thing being avoided.
 *
 * Consecutive duplicates are collapsed so holding Enter on one line does not
 * push the rest of the history out of the ring.
 */
class PromptHistoryRing(
    private val limit: Int = DEFAULT_LIMIT,
    private val redactionFilter: RedactionFilter = RedactionFilter()
) {
    init {
        require(limit > 0) { "history limit must be positive" }
    }

    private val lanes: Map<PromptHistoryLane, MutableList<String>> =
        PromptHistoryLane.entries.associateWith { mutableListOf() }

    /**
     * Stores [value] in [lane], oldest-first.
     *
     * Blank lines are not recorded: they carry nothing to recall and would
     * otherwise consume a slot every time an operator pressed Enter on an empty
     * prompt.
     */
    fun record(lane: PromptHistoryLane, value: String) {
        if (value.isBlank()) return
        val entries = lanes.getValue(lane)
        val redacted = redactionFilter.redact(value)
        if (entries.lastOrNull() == redacted) return
        entries += redacted
        while (entries.size > limit) {
            entries.removeAt(0)
        }
    }

    /** Oldest-first snapshot of one lane. */
    fun entries(lane: PromptHistoryLane): List<String> = lanes.getValue(lane).toList()

    fun isEmpty(lane: PromptHistoryLane): Boolean = lanes.getValue(lane).isEmpty()

    fun size(lane: PromptHistoryLane): Int = lanes.getValue(lane).size

    /**
     * The entry [stepsBack] positions from the newest end, or null when that
     * position is outside the ring.
     *
     * Callers index by distance from the present rather than by absolute
     * position so that eviction of an old entry does not silently shift what
     * "one back" means.
     */
    fun recall(lane: PromptHistoryLane, stepsBack: Int): String? {
        if (stepsBack < 0) return null
        val entries = lanes.getValue(lane)
        return entries.getOrNull(entries.lastIndex - stepsBack)
    }

    /** Newest entry containing [needle], case-insensitively. Null when nothing matches. */
    fun searchBackwards(lane: PromptHistoryLane, needle: String): String? {
        val entries = lanes.getValue(lane)
        if (entries.isEmpty()) return null
        if (needle.isEmpty()) return entries.last()
        return entries.asReversed().firstOrNull { it.contains(needle, ignoreCase = true) }
    }

    private companion object {
        const val DEFAULT_LIMIT = 100
    }
}
