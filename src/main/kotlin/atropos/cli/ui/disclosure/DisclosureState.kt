/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.ui.disclosure

/**
 * Whether a disclosure row is closed, and if open, how deep.
 *
 * HOE-B02 fixes the default: every row starts [Collapsed]. That is not a
 * cosmetic preference — a transcript that opens Thinking, Plan, Evidence,
 * Engine and Checkpoint by default buries the assistant's actual answer under
 * machinery, which is how agent CLIs become unreadable on an 80-column
 * terminal. [DEFAULT] exists so no caller has to decide, and so no caller can
 * quietly decide otherwise by forgetting an argument.
 *
 * [Collapsed] is deliberately *not* modelled as a level. If it were an "L0"
 * enum case, arithmetic like `level.depth - 1` would wander into it and code
 * would start treating "closed" as "showing a bit". Here the compiler forces a
 * caller to acknowledge the closed case before it can name a depth.
 *
 * Transitions live in [DisclosureExpansion], not on this type: the state knows
 * where it is, and the transition rules — first expand reveals L1 only, expands
 * never shrink — are policy that belongs in one auditable place.
 */
sealed interface DisclosureState {

    /** The level currently revealed, or `null` when closed. */
    val revealed: DisclosureLevel?

    /** Closed. The row shows its summary line and nothing else. */
    data object Collapsed : DisclosureState {
        override val revealed: DisclosureLevel? get() = null
    }

    /**
     * Open, showing everything from L1 through [level].
     *
     * Note the plural implication: an `Expanded(L3)` row shows L1, L2 *and* L3,
     * because [DisclosureContent.visibleAt] accumulates. There is no state that
     * means "showing only L3".
     */
    data class Expanded(val level: DisclosureLevel) : DisclosureState {
        override val revealed: DisclosureLevel get() = level
        override fun toString(): String = "Expanded(${level.label})"
    }

    /** True when the row is open at any depth. */
    val isOpen: Boolean get() = revealed != null

    companion object {
        /** HOE-B02's mandated starting state for every row. */
        val DEFAULT: DisclosureState = Collapsed
    }
}
