/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.ui.design

/**
 * Responsive breakpoints for terminal width-safe composition.
 * Allows Surface and renderers to adapt layout to available width.
 *
 * The tier names and their cut points are the native responsive grammar:
 * `COMPACT(<60) · MEDIUM · WIDE(100+)`. Every renderer resolves width through
 * [of] rather than comparing raw columns, so "narrow" means the same thing in
 * the dashboard, the dialogs, the disclosure rows and the sticky chrome. A
 * renderer that invented its own threshold would drift out of step with the
 * rest of the UI the first time the grammar changed.
 *
 * Supports Termux (40-80 cols) through desktop (120+ cols).
 *
 * ### Why four tiers for a three-tier grammar
 *
 * The grammar names three width classes; the enum carries a fourth, [ULTRA].
 * It is kept, not folded into [WIDE], for two reasons:
 *
 *  - [WIDE] is defined as "100 or more columns", and that band is unbounded.
 *    A 100-column terminal and a 220-column ultrawide are both WIDE by the
 *    grammar, but they are not the same layout problem: at 100 columns a
 *    three-column card grid is comfortable, at 220 it leaves a card stretched
 *    across a third of a metre of screen. [ULTRA] is a refinement *inside*
 *    WIDE — "wide, and then some" — not a fourth peer class. Anything the
 *    grammar says about WIDE is also true of ULTRA.
 *  - Consumers branch on it exhaustively (`Surface.columnsFor`,
 *    `ContextSightPillLine.budget`, `DashboardRenderer.maxProjects` /
 *    `maxWorkItems` are all `when` expressions over all four constants).
 *    Deleting the constant would not simplify the grammar, it would delete
 *    those layouts' widest case and stop three files compiling.
 *
 * [ULTRA]'s cut point stays at 160 columns, where it already was. The width
 * ladder 40/80/120/160 is what the parity baselines under
 * `docs/ui-parity/baseline/` are captured at, and 160 is the only one of those
 * that plausibly reads as "very wide". Moving the boundary would reclassify
 * captured evidence for no layout reason.
 */
enum class Breakpoint {
  /** Phone / narrow Termux window: below [COMPACT_MAX_EXCLUSIVE] columns. */
  COMPACT,

  /** Standard terminal: [COMPACT_MAX_EXCLUSIVE] up to [WIDE_MIN] exclusive. */
  MEDIUM,

  /** Wide terminal: [WIDE_MIN] columns or more, below [ULTRA_MIN]. */
  WIDE,

  /** Very wide terminal: [ULTRA_MIN] columns or more. A refinement of [WIDE]. */
  ULTRA;

  companion object {
    /** First column count that is no longer [COMPACT]. Grammar: `COMPACT(<60)`. */
    const val COMPACT_MAX_EXCLUSIVE: Int = 60

    /** First column count that counts as wide. Grammar: `WIDE(100+)`. */
    const val WIDE_MIN: Int = 100

    /** First column count that counts as very wide; a sub-band of [WIDE]. */
    const val ULTRA_MIN: Int = 160

    /**
     * Determine breakpoint from terminal width.
     *
     * Conservative: prefers narrower breakpoint over wider when ambiguous. A
     * boundary width belongs to the *wider* tier only once it has fully reached
     * it — 59 columns is [COMPACT] and 60 is [MEDIUM]; 99 is [MEDIUM] and 100 is
     * [WIDE] — and any width the caller could not have meant (zero, negative, a
     * failed `tput cols`) resolves to [COMPACT] rather than throwing. Layout
     * code runs on every frame, so a bad width must degrade to the safest
     * layout, not take the renderer down.
     */
    fun of(width: Int): Breakpoint = when {
      width < COMPACT_MAX_EXCLUSIVE -> COMPACT
      width < WIDE_MIN -> MEDIUM
      width < ULTRA_MIN -> WIDE
      else -> ULTRA
    }
  }
}
