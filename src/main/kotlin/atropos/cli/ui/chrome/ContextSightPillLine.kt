/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.ui.chrome

import atropos.cli.ui.TerminalText
import atropos.cli.ui.design.Breakpoint

/**
 * Fits a [ContextSightPill] onto one line inside a width budget.
 *
 * The pill sits between the brand and the checkpoint age in a sticky header, so
 * it cannot be allowed to grow with its content: a longer provider name must not
 * push the age off the row, because a field that appears and disappears between
 * frames is the same visual defect as chrome that moves. The budget is therefore
 * a function of [Breakpoint] — the width vocabulary that already exists — and
 * never of the pill's own text.
 *
 * Truncation drops whole fields from the least significant end, and only then
 * ellipsizes. The most significant field is never dropped: on a 40-column phone
 * terminal the operator still learns whether the context was attested, even if
 * they lose the hash and the provider. Losing that field to save four cells
 * would trade the one piece of information the pill exists to carry.
 *
 * Plain text only. Cell measurement goes through `TerminalText` so wide glyphs
 * are counted the way the canvas counts them, rather than by `String.length`.
 */
object ContextSightPillLine {

    /** Separator between pill fields. Matches the ` · ` the rest of the UI uses. */
    const val SEPARATOR = " · "

    /**
     * The result of fitting, with the discarded fields kept.
     *
     * [dropped] is returned rather than thrown away so a caller — or a test —
     * can assert that the most significant field survived, instead of inferring
     * it from a rendered string.
     */
    data class Fitted(
        val text: String,
        val kept: List<ContextSightPill.Field>,
        val dropped: List<ContextSightPill.Field>
    ) {
        val cells: Int get() = TerminalText.cellWidth(text)
    }

    /**
     * Maximum cells the pill may occupy at each width class.
     *
     * Chosen so the pill stays a glance-sized indicator rather than a status
     * line: even at [Breakpoint.ULTRA] it is capped, because the header's spare
     * width belongs to the transcript's sense of space, not to more chrome.
     */
    fun budget(breakpoint: Breakpoint): Int = when (breakpoint) {
        Breakpoint.COMPACT -> 18
        Breakpoint.MEDIUM -> 30
        Breakpoint.WIDE -> 42
        Breakpoint.ULTRA -> 52
    }

    /**
     * Fits the pill for a terminal of [columns] cells, using that width's budget.
     *
     * Named apart from [fit] on purpose: both take two Ints, so as overloads they
     * collided on the JVM and the call was ambiguous. The distinction is real —
     * this one derives the budget from the terminal width, [fit] is told the
     * budget outright — so the names say which one the caller means.
     */
    fun fitForColumns(pill: ContextSightPill, columns: Int): Fitted =
        fit(pill, budget(Breakpoint.of(columns)))

    /**
     * Fits the pill into exactly [availableCells].
     *
     * A non-positive budget yields empty text with every field reported as
     * dropped — an honest "there was no room" rather than a one-character stub
     * that looks like data.
     */
    fun fit(pill: ContextSightPill, availableCells: Int): Fitted {
        val fields = pill.fields()
        if (availableCells <= 0) return Fitted("", emptyList(), fields)

        var kept = fields
        while (kept.size > 1 && TerminalText.cellWidth(join(kept)) > availableCells) {
            kept = kept.dropLast(1)
        }

        val joined = join(kept)
        if (TerminalText.cellWidth(joined) <= availableCells) {
            return Fitted(joined, kept, fields.drop(kept.size))
        }

        // Only the most significant field remains and it still does not fit.
        // Ellipsize it — dropping it would leave the pill saying nothing at all.
        val survivor = kept.first()
        return Fitted(
            text = TerminalText.ellipsize(survivor.text, availableCells),
            kept = listOf(survivor),
            dropped = fields.drop(1)
        )
    }

    private fun join(fields: List<ContextSightPill.Field>): String =
        fields.joinToString(SEPARATOR) { it.text }
}
