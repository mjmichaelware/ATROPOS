package atropos.cli.ui

import atropos.cli.ui.design.Breakpoint

/** Shared width grammar for native terminal layouts. */
class ResponsiveNativeGrammar {
    private val baselineSnapshots = BaselineSnapshots()
    data class Layout(val breakpoint: Breakpoint, val columns: Int, val maxLabelWidth: Int)

    fun layout(width: Int): Layout {
        val breakpoint = Breakpoint.of(width)
        return Layout(
            breakpoint = breakpoint,
            columns = when (breakpoint) {
                Breakpoint.COMPACT -> 1
                Breakpoint.MEDIUM -> 2
                Breakpoint.WIDE -> 3
                Breakpoint.ULTRA -> 4
            },
            maxLabelWidth = when (breakpoint) {
                Breakpoint.COMPACT -> 18
                Breakpoint.MEDIUM -> 28
                Breakpoint.WIDE -> 40
                Breakpoint.ULTRA -> 56
            }
        )
    }

    fun baselineWidths(): List<Int> = baselineSnapshots.widths()
}
