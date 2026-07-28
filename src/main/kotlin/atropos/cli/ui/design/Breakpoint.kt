/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.ui.design

/**
 * Responsive breakpoints for terminal width-safe composition.
 * Allows Surface and renderers to adapt layout to available width.
 *
 * Supports Termux (40-80 cols) through desktop (120+ cols).
 */
enum class Breakpoint {
  COMPACT,   // < 80 columns (mobile/narrow)
  MEDIUM,    // 80-119 columns (standard terminal)
  WIDE,      // 120-159 columns (wide terminal)
  ULTRA;     // 160+ columns (very wide)

  companion object {
    /**
     * Determine breakpoint from terminal width.
     * Conservative: prefers narrower breakpoint over wider when ambiguous.
     */
    fun of(width: Int): Breakpoint = when {
      width < 80 -> COMPACT
      width < 120 -> MEDIUM
      width < 160 -> WIDE
      else -> ULTRA
    }
  }
}
