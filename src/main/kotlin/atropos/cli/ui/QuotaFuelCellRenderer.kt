/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.ui

import atropos.cli.ui.design.Role

/**
 * HOE-E04: Quota fuel cell + ghost burn + lock.
 * Spend as a depleting core; projected cost as translucent ghost burn.
 */
class QuotaFuelCellRenderer(private val theme: TerminalTheme) {
    data class QuotaState(val used: Double, val limit: Double, val projected: Double = 0.0)

    fun render(state: QuotaState, width: Int = 20): String {
        val safeWidth = width.coerceAtLeast(10)
        if (state.limit <= 0) return "[ no limit ]".padEnd(safeWidth)
        
        val overLimit = state.used >= state.limit
        if (overLimit) {
            return theme.format("[ LOCKED : QUOTA EXCEEDED ]", Role.STATUS_ERROR).padEnd(safeWidth)
        }
        
        val fillWidth = ((state.used / state.limit) * safeWidth).toInt().coerceIn(0, safeWidth)
        val ghostWidth = ((state.projected / state.limit) * safeWidth).toInt().coerceIn(0, safeWidth - fillWidth)
        val emptyWidth = safeWidth - fillWidth - ghostWidth
        
        val fill = "█".repeat(fillWidth)
        val ghost = "▒".repeat(ghostWidth)
        val empty = "░".repeat(emptyWidth)
        
        val warning = if (state.used + state.projected >= state.limit * 0.9) Role.STATUS_ERROR else Role.STATUS_PENDING
        return theme.format(fill, warning) + theme.format(ghost, Role.MUTED) + theme.format(empty, Role.MUTED)
    }
}
