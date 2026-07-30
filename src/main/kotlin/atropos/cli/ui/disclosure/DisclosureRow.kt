/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.ui.disclosure

/**
 * One disclosure row: which of the five it is, what it can reveal, and how far
 * it is currently open.
 *
 * Immutable. [expand] and [collapse] return a new row rather than mutating this
 * one, because a transcript keeps many rows and a repaint must be able to render
 * a consistent snapshot; in-place mutation is how a redraw ends up showing one
 * row's old state next to another row's new state.
 *
 * The [state] default is [DisclosureState.DEFAULT], i.e. collapsed, so
 * HOE-B02's "default collapsed" holds for any construction that does not
 * explicitly say otherwise — including future call sites nobody has written yet.
 *
 * This type holds no terminal knowledge at all: no width, no colour, no glyph.
 * Presentation is [DisclosureRowFormatter]'s job, so the state machine can be
 * exercised without a terminal and the formatter can be exercised without a
 * state machine.
 */
data class DisclosureRow(
    val kind: DisclosureRowKind,
    val content: DisclosureContent,
    val state: DisclosureState = DisclosureState.DEFAULT
) {

    /** Label as HOE-B02 spells it. */
    val label: String get() = kind.label

    /** True when a further expand would reveal something. */
    val canExpand: Boolean get() = DisclosureExpansion.canExpand(state, content)

    /** Lines currently on screen below the row header. Empty while collapsed. */
    fun visibleLines(): List<String> =
        state.revealed?.let { content.visibleAt(it) } ?: emptyList()

    /**
     * Expands one level, returning the new row and the lines that step added.
     *
     * Returns `null` when there is nothing deeper, so a caller cannot mistake a
     * dead keypress for a successful expand that happened to add no lines.
     * The first successful expand on a collapsed row always lands on
     * [DisclosureLevel.SHALLOWEST] — HOE-B02's "L1 only on first expand".
     */
    fun expand(): Expansion? {
        val reveal = DisclosureExpansion.expand(state, content) ?: return null
        return Expansion(copy(state = reveal.to), reveal)
    }

    /** Closes the row back to its summary line. Always succeeds. */
    fun collapse(): DisclosureRow = copy(state = DisclosureExpansion.collapse())

    /**
     * The result of a successful [expand]: the row afterwards, plus the reveal
     * describing exactly what was added.
     *
     * Both halves are returned because streaming renderers want [reveal] and
     * repainting renderers want [row]; giving each only what it needs stops a
     * streaming path from re-emitting content the user is already looking at.
     */
    data class Expansion(val row: DisclosureRow, val reveal: DisclosureReveal)

    companion object {
        /** Builds a collapsed row. The only constructor call sites should need. */
        fun collapsed(kind: DisclosureRowKind, content: DisclosureContent): DisclosureRow =
            DisclosureRow(kind, content, DisclosureState.Collapsed)
    }
}
