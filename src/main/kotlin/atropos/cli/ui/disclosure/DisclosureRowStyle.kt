/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.ui.disclosure

import atropos.cli.ui.design.Breakpoint

/**
 * Everything [DisclosureRowFormatter] needs to know about the terminal, gathered
 * into one value so the formatter itself can stay a pure function.
 *
 * Probing the environment inside a formatter is what makes rendering
 * untestable and non-deterministic: the same row then formats differently
 * depending on the machine, and a `NO_COLOR` regression only shows up on a
 * user's terminal. The caller resolves these once — it already knows the width
 * and the ASCII mode from `TerminalTheme` — and passes the answer down.
 *
 * [asciiOnly] carries the release-blocking case. When it is true every glyph
 * this package emits falls back to ASCII, matching the pairing
 * `atropos.cli.ui.design.RunState` uses for status glyphs.
 *
 * [breakpoint] exists because the level suffix ("L2 of L4") is the first thing
 * that should go on a 40-column Termux window: it is orientation, not content,
 * and losing it costs the reader less than losing the row label to truncation.
 */
data class DisclosureRowStyle(
    /** Total columns available for the row, including [indent]. */
    val width: Int,
    /** True on `NO_COLOR` / `TERM=dumb` / non-Unicode terminals. */
    val asciiOnly: Boolean = false,
    /** Columns of left padding, e.g. to sit inside a transcript rail. */
    val indent: Int = 0
) {

    /** Responsive class derived from [width]; never re-derived downstream. */
    val breakpoint: Breakpoint = Breakpoint.of(width)

    /** Columns left for text after [indent]. Floored so arithmetic stays sane. */
    val contentWidth: Int = (width - indent).coerceAtLeast(MINIMUM_CONTENT)

    /** Truncation mark, ASCII-safe. */
    val ellipsis: String get() = if (asciiOnly) "..." else "…"

    /**
     * Whether to append the "L2 of L4" orientation suffix. Dropped on
     * [Breakpoint.COMPACT], where those cells belong to the label.
     */
    val showsLevelSuffix: Boolean get() = breakpoint != Breakpoint.COMPACT

    companion object {
        /**
         * Narrowest content column the formatter will admit. Below this, output
         * would be ellipsis-only, which communicates nothing; clamping keeps a
         * misconfigured width from producing a column of dots.
         */
        const val MINIMUM_CONTENT: Int = 12

        /** Sensible default for a standard terminal with no rail. */
        val DEFAULT: DisclosureRowStyle = DisclosureRowStyle(width = 80)

        /** ASCII-only variant of [DEFAULT], for `TERM=dumb` paths. */
        val ASCII: DisclosureRowStyle = DisclosureRowStyle(width = 80, asciiOnly = true)
    }
}
