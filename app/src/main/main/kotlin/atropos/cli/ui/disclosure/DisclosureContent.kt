/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.ui.disclosure

/**
 * The detail a disclosure row can reveal, stored as *what each level adds*
 * rather than as what each level shows.
 *
 * This representation is the enforcement mechanism for HOE-A08's
 * "information never removed". A type that stored a full view per level would
 * let a caller hand L2 a shorter list than L1 and silently drop lines the user
 * had already read — the exact regression the atom forbids, and one that no
 * amount of downstream review reliably catches. Here there is nowhere to put
 * such a list: a level owns only its [additions], and [visibleAt] is defined as
 * the concatenation of every addition from L1 through the requested level. The
 * superset property is therefore a consequence of the data shape, not a rule
 * anyone has to remember.
 *
 * Two invariants are checked at construction, both from real failure modes:
 *
 *  - **Contiguity.** Levels must be populated from L1 outward with no gaps. A
 *    row holding L1 and L3 content but nothing at L2 renders an expand that
 *    reveals nothing — a dead keypress that reads as a hung UI.
 *  - **Non-emptiness of populated levels.** A level present in the map with an
 *    empty or all-blank body is the same dead keypress, spelled differently.
 *
 * [summary] is separate from the levels on purpose. It is the one-line gist
 * shown on the collapsed row, so it is visible at *every* state including
 * collapsed, and is never part of a level's additions — otherwise the first
 * expand would repeat the line the user is already looking at.
 */
class DisclosureContent private constructor(
    /** Gist shown on the collapsed row. Always visible, at every state. */
    val summary: String,
    private val additions: Map<DisclosureLevel, List<String>>
) {

    /** The deepest level holding content, or `null` when there is nothing to expand. */
    val deepest: DisclosureLevel? = DisclosureLevel.ordered().lastOrNull { additions.containsKey(it) }

    /** True when this row has no detail at all and must not offer an expand affordance. */
    val isLeaf: Boolean get() = deepest == null

    /** Lines this level contributes on top of the level above it. Never null-signalling. */
    fun additionsAt(level: DisclosureLevel): List<String> = additions[level] ?: emptyList()

    /**
     * Everything visible once expanded to [level], shallowest content first.
     *
     * Guaranteed by construction: `visibleAt(deeper)` starts with exactly
     * `visibleAt(shallower)` for every deeper level. Callers may rely on that
     * prefix property — it is what makes an expand feel like the page growing
     * rather than the page being replaced.
     */
    fun visibleAt(level: DisclosureLevel): List<String> =
        level.throughHere().flatMap { additionsAt(it) }

    /** True when a row already at [level] has anything deeper left to offer. */
    fun hasDeeperThan(level: DisclosureLevel): Boolean {
        val floor = deepest ?: return false
        return floor.depth > level.depth
    }

    override fun toString(): String =
        "DisclosureContent(summary='$summary', deepest=$deepest)"

    companion object {

        /** A row with a gist and nothing to expand into. */
        fun leaf(summary: String): DisclosureContent =
            DisclosureContent(summary.trim(), emptyMap())

        /**
         * Builds content from per-level additions.
         *
         * Levels absent from [additions] are treated as absent, not empty, and
         * the contiguity invariant is enforced here rather than at render time,
         * so a malformed row fails where it is authored instead of producing a
         * silently inert expand in front of an operator.
         */
        fun of(summary: String, additions: Map<DisclosureLevel, List<String>>): DisclosureContent {
            val cleaned = additions
                .mapValues { (_, lines) -> lines.filter { it.isNotBlank() } }
                .filterValues { it.isNotEmpty() }

            require(cleaned.size == additions.count { it.value.isNotEmpty() }) {
                "disclosure level present with blank-only body: ${
                    additions.keys.filter { it !in cleaned.keys }
                }"
            }

            val expected = DisclosureLevel.ordered().take(cleaned.size).toSet()
            require(cleaned.keys == expected) {
                "disclosure levels must be contiguous from L1; got ${
                    cleaned.keys.sortedBy { it.depth }.map { it.label }
                }"
            }

            return DisclosureContent(summary.trim(), cleaned)
        }

        /**
         * Positional convenience for the common authoring shape. Passing a
         * deeper level while a shallower one is empty is rejected by [of].
         */
        fun of(
            summary: String,
            l1: List<String> = emptyList(),
            l2: List<String> = emptyList(),
            l3: List<String> = emptyList(),
            l4: List<String> = emptyList()
        ): DisclosureContent = of(
            summary,
            mapOf(
                DisclosureLevel.L1 to l1,
                DisclosureLevel.L2 to l2,
                DisclosureLevel.L3 to l3,
                DisclosureLevel.L4 to l4
            ).filterValues { it.isNotEmpty() }
        )
    }
}
