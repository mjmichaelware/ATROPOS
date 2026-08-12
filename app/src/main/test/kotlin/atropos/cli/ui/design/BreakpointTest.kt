/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.ui.design

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The responsive grammar is `COMPACT(<60) · MEDIUM · WIDE(100+)`, with [Breakpoint.ULTRA]
 * as a sub-band of WIDE at 160 columns.
 *
 * These are boundary tests on purpose. Every renderer in `atropos.cli.ui` asks
 * [Breakpoint.of] which layout to use, so a threshold that is off by one column
 * is not a cosmetic error: it silently moves the width at which the dashboard
 * drops its question suffixes, the dialog stops being full-bleed and the
 * adapter table stops stacking. An acceptance check for "operable at 70
 * columns" would then pass or fail for the wrong reason.
 */
class BreakpointTest {

    // ---- exact boundaries ---------------------------------------------------

    @Test
    fun compact_ends_at_59_and_medium_begins_at_60() {
        assertEquals(Breakpoint.COMPACT, Breakpoint.of(59), "59 columns is still COMPACT")
        assertEquals(Breakpoint.MEDIUM, Breakpoint.of(60), "60 columns is the first MEDIUM width")
    }

    @Test
    fun medium_ends_at_99_and_wide_begins_at_100() {
        assertEquals(Breakpoint.MEDIUM, Breakpoint.of(99), "99 columns is still MEDIUM")
        assertEquals(Breakpoint.WIDE, Breakpoint.of(100), "100 columns is the first WIDE width")
    }

    @Test
    fun wide_ends_at_159_and_ultra_begins_at_160() {
        assertEquals(Breakpoint.WIDE, Breakpoint.of(159), "159 columns is still WIDE")
        assertEquals(Breakpoint.ULTRA, Breakpoint.of(160), "160 columns is the first ULTRA width")
    }

    @Test
    fun published_thresholds_match_the_resolver() {
        // The constants exist so consumers and tests can name the boundary
        // instead of repeating the literal. If they drift from `of`, callers
        // that trust them are silently wrong.
        assertEquals(Breakpoint.COMPACT, Breakpoint.of(Breakpoint.COMPACT_MAX_EXCLUSIVE - 1))
        assertEquals(Breakpoint.MEDIUM, Breakpoint.of(Breakpoint.COMPACT_MAX_EXCLUSIVE))
        assertEquals(Breakpoint.MEDIUM, Breakpoint.of(Breakpoint.WIDE_MIN - 1))
        assertEquals(Breakpoint.WIDE, Breakpoint.of(Breakpoint.WIDE_MIN))
        assertEquals(Breakpoint.WIDE, Breakpoint.of(Breakpoint.ULTRA_MIN - 1))
        assertEquals(Breakpoint.ULTRA, Breakpoint.of(Breakpoint.ULTRA_MIN))
    }

    // ---- monotonicity ------------------------------------------------------

    @Test
    fun of_is_monotonic_in_width() {
        // A narrower terminal must never be handed a wider layout. Checked over
        // the whole plausible range rather than at sampled points, because an
        // inverted `when` arm is exactly the kind of defect sampling misses.
        var previous = Breakpoint.of(-100)
        for (width in -100..400) {
            val current = Breakpoint.of(width)
            assertTrue(
                current.ordinal >= previous.ordinal,
                "width $width resolved to $current, narrower than $previous at width ${width - 1}"
            )
            previous = current
        }
    }

    @Test
    fun every_tier_is_reachable() {
        // A tier no width can produce is a layout branch that can never run.
        val reached = (-10..400).map { Breakpoint.of(it) }.toSet()
        assertEquals(Breakpoint.entries.toSet(), reached, "unreachable tiers: ${Breakpoint.entries.toSet() - reached}")
    }

    // ---- the documented gap-map cases --------------------------------------

    @Test
    fun a_40_column_termux_terminal_is_compact() {
        assertEquals(Breakpoint.COMPACT, Breakpoint.of(40))
    }

    @Test
    fun a_70_column_terminal_is_medium_not_compact() {
        // 70 sits above the 60-column COMPACT ceiling, so it gets the standard
        // layout. Under the previous thresholds (COMPACT < 80) it was treated as
        // a phone: stacked adapter tables, no question suffixes, a full-bleed
        // dialog. That was the divergence this atom closes.
        assertEquals(Breakpoint.MEDIUM, Breakpoint.of(70))
    }

    @Test
    fun a_100_column_terminal_is_wide_not_medium() {
        // The grammar says WIDE begins at 100. Under the previous thresholds
        // (WIDE at 120) a 100-column terminal was MEDIUM and lost the wide-only
        // affordances it has room for.
        assertEquals(Breakpoint.WIDE, Breakpoint.of(100))
    }

    @Test
    fun the_parity_baseline_widths_resolve_to_distinct_tiers() {
        // docs/ui-parity/baseline/ captures 40/80/120/160. Four captures that
        // collapsed onto fewer than four tiers would stop being evidence about
        // the responsive grammar.
        assertEquals(
            listOf(Breakpoint.COMPACT, Breakpoint.MEDIUM, Breakpoint.WIDE, Breakpoint.ULTRA),
            listOf(40, 80, 120, 160).map { Breakpoint.of(it) }
        )
    }

    // ---- degenerate input --------------------------------------------------

    @Test
    fun zero_and_negative_widths_resolve_to_the_narrowest_tier() {
        // A failed `tput cols` yields 0 or -1. Layout runs every frame, so the
        // safe answer is the narrowest layout, never an exception.
        assertEquals(Breakpoint.COMPACT, Breakpoint.of(0))
        assertEquals(Breakpoint.COMPACT, Breakpoint.of(-1))
        assertEquals(Breakpoint.COMPACT, Breakpoint.of(Int.MIN_VALUE))
    }

    @Test
    fun absurdly_large_widths_resolve_to_the_widest_tier() {
        assertEquals(Breakpoint.ULTRA, Breakpoint.of(10_000))
        assertEquals(Breakpoint.ULTRA, Breakpoint.of(Int.MAX_VALUE))
    }

    @Test
    fun no_width_throws() {
        // Includes the overflow-prone extremes; `of` must be total.
        val widths = listOf(Int.MIN_VALUE, -1, 0, 1, 59, 60, 99, 100, 159, 160, Int.MAX_VALUE)
        widths.forEach { width ->
            val resolved = Breakpoint.of(width)
            assertTrue(resolved in Breakpoint.entries, "width $width resolved outside the enum")
        }
    }
}
