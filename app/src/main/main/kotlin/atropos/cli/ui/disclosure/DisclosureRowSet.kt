/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.ui.disclosure

/**
 * The disclosure rows attached to one transcript entry, in HOE-B02's fixed
 * order, all collapsed until asked otherwise.
 *
 * A transcript entry does not get to choose its row order or invent rows; it
 * supplies content for whichever of the five it has, and this type places them.
 * Rows with no content are omitted rather than drawn as empty expandables,
 * because an affordance that reveals nothing when pressed reads as a hung UI —
 * the same reason [DisclosureContent] refuses to hold a gap between levels.
 *
 * Immutable: [expand] and [collapse] return a new set. That is what lets a
 * renderer diff two snapshots and repaint only the rows that actually moved,
 * instead of redrawing the block and losing the reader's place.
 *
 * Expanding one row never touches the others. That independence is deliberate:
 * an operator opening Evidence to check a citation must not have Thinking
 * unfold under their cursor and push the thing they were reading off screen.
 */
class DisclosureRowSet private constructor(
    /** Present rows only, already in [DisclosureRowKind.ordered] order. */
    val rows: List<DisclosureRow>
) {

    /** True when this entry has no disclosure rows at all — draw nothing. */
    val isEmpty: Boolean get() = rows.isEmpty()

    /** The row for [kind], or `null` when this entry has no such row. */
    fun row(kind: DisclosureRowKind): DisclosureRow? = rows.firstOrNull { it.kind == kind }

    /**
     * Expands one row by one level.
     *
     * Returns `null` when the row is absent or already fully revealed, so the
     * caller can leave the screen untouched. Every other row keeps its state
     * byte-for-byte.
     */
    fun expand(kind: DisclosureRowKind): Change? {
        val target = row(kind) ?: return null
        val expansion = target.expand() ?: return null
        return Change(replace(expansion.row), expansion.reveal)
    }

    /** Collapses one row back to its summary. Returns `null` if the row is absent. */
    fun collapse(kind: DisclosureRowKind): DisclosureRowSet? {
        val target = row(kind) ?: return null
        return replace(target.collapse())
    }

    /**
     * Collapses every row.
     *
     * The inverse "expand every row" is deliberately absent: five rows opened at
     * once is the wall of text HOE-A08 exists to prevent, and offering it as one
     * call would make it the path of least resistance for the next caller.
     */
    fun collapseAll(): DisclosureRowSet =
        DisclosureRowSet(rows.map { it.collapse() })

    private fun replace(updated: DisclosureRow): DisclosureRowSet =
        DisclosureRowSet(rows.map { if (it.kind == updated.kind) updated else it })

    /** A successful [expand]: the set afterwards, and what that step revealed. */
    class Change(val set: DisclosureRowSet, val reveal: DisclosureReveal)

    companion object {

        /** An entry with no disclosure rows. */
        val EMPTY: DisclosureRowSet = DisclosureRowSet(emptyList())

        /**
         * Builds the set from whatever content an entry has.
         *
         * Ordering comes from [DisclosureRowKind.ordered], not from the map's
         * iteration order, so a caller cannot change the on-screen sequence by
         * building its map in a different order.
         */
        fun of(content: Map<DisclosureRowKind, DisclosureContent>): DisclosureRowSet =
            DisclosureRowSet(
                DisclosureRowKind.ordered().mapNotNull { kind ->
                    content[kind]?.let { DisclosureRow.collapsed(kind, it) }
                }
            )
    }
}
