/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.ui.chrome

/**
 * The outcome of asking for a sticky layout: either a sound partition, or a
 * typed refusal explaining why this terminal cannot hold the chrome.
 *
 * A terminal three rows tall cannot show a header, an input and a line of
 * transcript at once. The tempting answer is to clamp — give the transcript
 * `coerceAtLeast(1)` rows and let the input overlap it — but that paints two
 * regions onto one row and is the direct cause of chrome that shifts between
 * frames. The honest answer is to refuse, so the caller can degrade
 * deliberately (drop the header, or tell the operator the window is too small)
 * rather than inherit a silently broken frame.
 *
 * Same discipline as `HomeStateProvider`: a fault must stay distinguishable
 * from a nominal state.
 */
sealed interface StickyRegionPlan {

    data class Resolved(val regions: StickyRegions) : StickyRegionPlan

    data class Refused(val reason: Reason, val detail: String) : StickyRegionPlan

    /** Why a layout could not be produced. */
    enum class Reason {
        /** Rows or columns were zero or negative — no viewport to lay out. */
        EMPTY_VIEWPORT,

        /** A caller asked for a negative header or input height. */
        NEGATIVE_REGION_REQUEST,

        /** Header + input + the minimum transcript does not fit in the available rows. */
        TOO_SHORT_FOR_CHROME
    }

    /** The partition when one exists, `null` on refusal. Never a clamped stand-in. */
    fun regionsOrNull(): StickyRegions? = when (this) {
        is Resolved -> regions
        is Refused -> null
    }

    /** One line an operator can read; refusals say what was needed, not just that it failed. */
    fun describe(): String = when (this) {
        is Resolved -> "header=${regions.header.rows} transcript=${regions.transcript.rows} " +
            "input=${regions.input.rows} of ${regions.totalRows} rows"
        is Refused -> "refused (${reason.name.lowercase().replace('_', ' ')}) · $detail"
    }
}
