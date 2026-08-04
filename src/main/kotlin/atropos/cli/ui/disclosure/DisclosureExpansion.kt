/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.ui.disclosure

/**
 * The only legal state transitions for a disclosure row.
 *
 * Keeping the rules here — rather than as methods on [DisclosureState] or as
 * `if` branches inside a renderer — means there is exactly one place to read
 * when asking "can an expand ever skip a level, or ever show everything at
 * once?". The answer must stay no, because both are the same regression: an
 * operator presses expand once and gets a wall of L1–L4 output, which is
 * indistinguishable from having no disclosure at all.
 *
 * Two rules, both from HOE-A08/HOE-B02:
 *
 *  1. A first expand reveals [DisclosureLevel.SHALLOWEST] and stops there.
 *  2. Each later expand advances exactly one level, and only if content exists
 *     at that level.
 *
 * There is deliberately no `expandAll` and no `setLevel`. Their absence is the
 * feature: a caller that wants L4 must walk L1, L2, L3 first, so the transcript
 * grows by steps the reader chose. A jump-to-depth entry point would let one
 * careless call site reintroduce the wall of text this atom exists to prevent.
 *
 * All functions are pure. Nothing here touches a terminal, a clock, or the
 * environment; state lives with the caller, which is what makes a repaint
 * deterministic.
 */
object DisclosureExpansion {

    /**
     * The level a further expand would reveal, or `null` when there is nothing
     * left. Renderers use this to decide whether to draw an expand affordance
     * at all — offering one that does nothing is a lie about what is available.
     */
    fun nextLevel(state: DisclosureState, content: DisclosureContent): DisclosureLevel? {
        val deepest = content.deepest ?: return null
        val current = state.revealed ?: return DisclosureLevel.SHALLOWEST
        val candidate = current.deeper() ?: return null
        return if (deepest.covers(candidate)) candidate else null
    }

    /** True when [state] can be expanded further against [content]. */
    fun canExpand(state: DisclosureState, content: DisclosureContent): Boolean =
        nextLevel(state, content) != null

    /**
     * Advances one level.
     *
     * Returns `null` — not an error and not an unchanged reveal — when the row
     * is already as deep as its content goes. A `null` means "the affordance
     * should not have been offered"; callers must treat it as a no-op and leave
     * the screen alone rather than repainting an identical block.
     */
    fun expand(state: DisclosureState, content: DisclosureContent): DisclosureReveal? {
        val next = nextLevel(state, content) ?: return null
        return DisclosureReveal(from = state, to = DisclosureState.Expanded(next), content = content)
    }

    /**
     * Closes the row completely, back to its summary line.
     *
     * Collapse intentionally does not step back one level. Partial collapse
     * would mean the same keypress sometimes hides L3 and sometimes hides
     * everything, depending on invisible history — and the atom's guarantee is
     * about expansion being additive, not about making close reversible in
     * halves. The summary survives, so nothing the collapsed row was
     * communicating is lost.
     *
     * Re-expanding afterwards starts again at [DisclosureLevel.SHALLOWEST] via
     * [expand], because a row that silently reopens four levels deep is the
     * wall of text arriving by the back door.
     *
     * It takes no current state on purpose: the destination cannot depend on
     * how deep the row happened to be, so there is no parameter for a future
     * edit to start branching on.
     */
    fun collapse(): DisclosureState.Collapsed = DisclosureState.Collapsed
}
