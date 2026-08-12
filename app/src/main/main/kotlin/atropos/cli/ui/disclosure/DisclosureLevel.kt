/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.ui.disclosure

/**
 * The four depths of progressive disclosure (HOE-A08).
 *
 * The authority is blunt about the shape: "Default collapsed; each expand
 * reveals only additional detail; never hide prior level." The failure mode it
 * exists to prevent is the one every agent CLI eventually ships — a single
 * "verbose" toggle that swaps one view for another, so the user loses the
 * summary they were reading the moment they ask for detail, and has to toggle
 * back and forth to hold both in their head.
 *
 * This enum is therefore an *ordering*, not a set of modes. [depth] is the only
 * comparison anything downstream is allowed to make, and every other type in
 * this package treats "deeper" as strictly additive. There is deliberately no
 * `L0`: a collapsed row is not level zero, it is the absence of a level, and
 * that distinction lives in [DisclosureState] where it cannot be arithmetically
 * confused with a depth.
 *
 * Four levels is a ceiling, not a target. [SHALLOWEST] is what a first expand
 * may reveal and [DEEPEST] is the most a row can ever hold; a row with only
 * [L1] content is normal and correct.
 */
enum class DisclosureLevel(
    /** 1-based nesting depth. Larger means more detail, never different detail. */
    val depth: Int,
    /** Operator-facing name, used by formatters that surface "showing L2 of L3". */
    val label: String
) {
    /** Headline detail. The most a first expand is ever allowed to reveal. */
    L1(1, "L1"),

    /** Supporting detail for a reader who has already read [L1]. */
    L2(2, "L2"),

    /** Diagnostic detail — the level an operator opens when something looks wrong. */
    L3(3, "L3"),

    /** Raw substrate: full payloads, full traces. Never shown unasked. */
    L4(4, "L4");

    /** The next deeper level, or `null` when already at [DEEPEST]. */
    fun deeper(): DisclosureLevel? = ORDERED.getOrNull(ordinal + 1)

    /**
     * Every level from [L1] through this one, in order.
     *
     * This is the whole "never hide prior level" rule expressed as a function:
     * anything resolving content for a level walks this list, so a deeper level
     * can only ever append to what a shallower one produced.
     */
    fun throughHere(): List<DisclosureLevel> = ORDERED.subList(0, ordinal + 1)

    /** True when [other] is this level or shallower — i.e. already revealed here. */
    fun covers(other: DisclosureLevel): Boolean = other.depth <= depth

    companion object {
        private val ORDERED: List<DisclosureLevel> = values().toList()

        /** The only level a first expand may reveal (HOE-B02: "L1 only"). */
        val SHALLOWEST: DisclosureLevel = L1

        /** The deepest level that exists. Nothing may reveal past it. */
        val DEEPEST: DisclosureLevel = L4

        /** All levels, shallowest first. Callers must not re-sort this. */
        fun ordered(): List<DisclosureLevel> = ORDERED
    }
}
