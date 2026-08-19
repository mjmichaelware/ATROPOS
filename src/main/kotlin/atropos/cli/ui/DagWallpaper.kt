/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.ui

import atropos.cli.ui.design.Role

/**
 * The graph you are building, as the thing behind the prompt.
 *
 * [ThreadTapestry] fills the home screen with cloth because an empty screen
 * teaches nobody anything. Cloth is honest when there is no run — but the
 * moment a run exists, the same rows can carry the run instead of a pattern,
 * and a pattern where information could have been is a wasted screen.
 *
 * Each cell is a node. Its weight says what state that node is in, so the
 * shape of the work — how much is ready, how much is blocked, how much is
 * finished — is readable at a glance from across the room, without a number
 * or a scroll.
 *
 * The idle art and the working art are deliberately the same object in the
 * same place: an operator learns one image, and it fills with meaning as their
 * run progresses rather than being replaced by a different screen.
 */
class DagWallpaper(private val theme: TerminalTheme) {

    enum class NodeState { DONE, RUNNING, READY, BLOCKED, FAILED }

    /**
     * @param states one entry per node, in execution order, so the picture
     *   reads left-to-right and top-to-bottom the way the graph runs.
     */
    fun render(states: List<NodeState>, width: Int, height: Int): List<String> {
        if (states.isEmpty() || width < MINIMUM_CELLS || height <= 0) return emptyList()

        val inner = width - MARGIN_COLUMNS * 2
        if (inner < MINIMUM_CELLS) return emptyList()

        val perRow = (inner + CELL_GAP) / (CELL_CELLS + CELL_GAP)
        if (perRow <= 0) return emptyList()

        val rowsNeeded = (states.size + perRow - 1) / perRow
        // A graph too tall for the space is summarised rather than cropped: a
        // cropped picture of four hundred nodes silently omits the tail, which
        // is exactly where the unfinished work is.
        val rows = minOf(rowsNeeded, (height - CAPTION_ROWS).coerceAtLeast(1))
        val shown = minOf(states.size, rows * perRow)
        val margin = " ".repeat(MARGIN_COLUMNS)

        val drawn = (0 until rows).map { row ->
            val slice = states.drop(row * perRow).take(perRow)
            margin + slice.joinToString(" ".repeat(CELL_GAP)) { state ->
                theme.paint(roleFor(state), glyphFor(state).repeat(CELL_CELLS))
            }
        }

        return drawn + caption(states, shown, width)
    }

    private fun caption(states: List<DagWallpaper.NodeState>, shown: Int, width: Int): List<String> {
        val done = states.count { it == NodeState.DONE }
        val blocked = states.count { it == NodeState.BLOCKED }
        val failed = states.count { it == NodeState.FAILED }
        val omitted = states.size - shown

        val parts = buildList {
            add("$done done")
            if (blocked > 0) add("$blocked blocked")
            if (failed > 0) add("$failed failed")
            add("${states.size} nodes")
            // Said, not hidden. An operator who cannot see the tail needs to
            // know the tail exists.
            if (omitted > 0) add("$omitted not shown")
        }
        return listOf(" ".repeat(MARGIN_COLUMNS) + theme.subdued(
            TerminalText.ellipsize(parts.joinToString(" · "), (width - MARGIN_COLUMNS * 2).coerceAtLeast(4))
        ))
    }

    private fun roleFor(state: NodeState): Role = when (state) {
        NodeState.DONE -> Role.STATUS_VERIFIED
        NodeState.RUNNING -> Role.ACCENT_FOCUS
        NodeState.READY -> Role.TEXT_SECONDARY
        NodeState.BLOCKED -> Role.TEXT_MUTED
        NodeState.FAILED -> Role.STATUS_FAILED
    }

    private fun glyphFor(state: NodeState): String = when (state) {
        NodeState.DONE -> "█"
        NodeState.RUNNING -> "▓"
        NodeState.READY -> "▒"
        NodeState.BLOCKED -> "░"
        NodeState.FAILED -> "▚"
    }

    private companion object {
        const val MINIMUM_CELLS = 20
        const val MARGIN_COLUMNS = 2
        const val CELL_CELLS = 2
        const val CELL_GAP = 1
        const val CAPTION_ROWS = 1
    }
}
