/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.ui.disclosure

/**
 * The twisty glyph on a disclosure row, with its ASCII fallback.
 *
 * HOE-B02 writes the collapsed row as `▸ Thinking`. That glyph is outside
 * ASCII, and this product treats `NO_COLOR` / `TERM=dumb` terminals as
 * release-blocking, so it cannot be the only spelling — the same pairing
 * `atropos.cli.ui.design.RunState` makes for status glyphs. A row whose marker
 * renders as `?` or a replacement box loses the one cue that says "there is more
 * here", and the detail becomes unreachable rather than merely ugly.
 *
 * These tokens live in this package rather than in
 * `atropos.cli.ui.design.Glyphs` because they are disclosure-specific state
 * markers, not shared box-drawing. If a later batch decides the twisty belongs
 * in the global token set, this object is the single place to delete.
 *
 * The marker is also never the *only* signal: the row always carries its text
 * label, and the formatter states the level in words, so an operator on a
 * terminal that mangles both arrows still reads the state.
 */
enum class DisclosureMarker(
    /** Preferred Unicode glyph. */
    val glyph: String,
    /** Fallback for ASCII-only terminals. Must stay single-width. */
    val asciiGlyph: String
) {
    /** Closed row: `▸ Thinking`. Points right — "opens this way". */
    COLLAPSED("▸", ">"),

    /** Open row: `▾ Thinking`. Points down — "content is below". */
    EXPANDED("▾", "v"),

    /**
     * Open row with nothing deeper left, and closed rows with no content at all.
     *
     * Distinct from [EXPANDED] on purpose: a row drawn with an arrow the user
     * can still press, that does nothing when pressed, teaches them the UI is
     * broken. This glyph says "this is all of it".
     */
    TERMINAL("·", ".");

    /** Resolves the glyph for the terminal the caller is on. */
    fun resolve(asciiOnly: Boolean): String = if (asciiOnly) asciiGlyph else glyph

    companion object {
        /**
         * Picks the marker for a row's state.
         *
         * [canExpand] comes from [DisclosureExpansion.canExpand] rather than
         * being recomputed here, so the glyph and the keybinding can never
         * disagree about whether an expand is available.
         */
        fun of(state: DisclosureState, canExpand: Boolean): DisclosureMarker = when {
            state.isOpen && !canExpand -> TERMINAL
            state.isOpen -> EXPANDED
            canExpand -> COLLAPSED
            else -> TERMINAL
        }
    }
}
