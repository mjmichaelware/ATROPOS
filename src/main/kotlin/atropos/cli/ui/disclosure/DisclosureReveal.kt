/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.ui.disclosure

/**
 * A single expand step, carrying the lines it added and nothing else.
 *
 * HOE-A08 says an expand "reveals only additional detail". This type is where
 * that claim is checked rather than asserted. Its [init] block rejects any step
 * whose destination is shallower than its origin, so a shrinking "expand"
 * cannot be constructed at all — there is no object to hand a renderer, and the
 * bug dies at the call site instead of as a line that vanished off an
 * operator's screen mid-run.
 *
 * [added] is derived, never supplied: it is [DisclosureContent.visibleAt] for
 * the destination with the origin's visible lines dropped from the front. That
 * subtraction is only sound because [DisclosureContent] guarantees the deeper
 * view begins with the shallower one verbatim, which is precisely why the
 * content model stores additions instead of views. If that guarantee ever
 * broke, [added] would stop being a suffix and the length arithmetic here would
 * surface it immediately.
 */
class DisclosureReveal(
    /** State before the expand. May be [DisclosureState.Collapsed]. */
    val from: DisclosureState,
    /** State after the expand. Always deeper than or equal to [from]. */
    val to: DisclosureState.Expanded,
    private val content: DisclosureContent
) {

    init {
        val origin = from.revealed
        require(origin == null || to.level.covers(origin)) {
            "expand may not go shallower: ${origin?.label} -> ${to.level.label}"
        }
        require(content.deepest?.covers(to.level) == true) {
            "no content at ${to.level.label}; deepest is ${content.deepest?.label ?: "none"}"
        }
    }

    /** Lines already on screen before this step. Unchanged by it, by definition. */
    val retained: List<String> = from.revealed?.let { content.visibleAt(it) } ?: emptyList()

    /**
     * The lines this step puts on screen, and only those.
     *
     * A caller streaming output appends exactly this; a caller repainting uses
     * [DisclosureContent.visibleAt] instead. Both paths agree because [retained]
     * plus [added] is that full view.
     */
    val added: List<String> = content.visibleAt(to.level).drop(retained.size)

    /** Everything visible after this step: [retained] then [added], in order. */
    fun visible(): List<String> = retained + added

    override fun toString(): String =
        "DisclosureReveal($from -> $to, +${added.size} lines)"
}
