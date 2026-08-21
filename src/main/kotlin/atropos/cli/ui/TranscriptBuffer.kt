/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.ui

class TranscriptBuffer(
    private val maximumBlocks: Int = 600
) {
    private val blocks = ArrayDeque<String>()
    private var scrollOffset = 0
    private var pendingNewOutput = 0
    private var lastRenderedWidth = 0

    init {
        require(maximumBlocks > 0)
    }

    val isEmpty: Boolean
        get() = blocks.isEmpty()

    val isFollowingTail: Boolean
        get() = scrollOffset == 0

    val newOutputCount: Int
        get() = pendingNewOutput

    fun append(value: String) {
        val wasAwayFromTail = scrollOffset > 0
        val appendedLines = if (lastRenderedWidth > 0) {
            AnsiLineWrapper.wrap(value, lastRenderedWidth).size
        } else {
            0
        }
        blocks.addLast(value)
        while (blocks.size > maximumBlocks) blocks.removeFirst()
        if (wasAwayFromTail) {
            scrollOffset = (scrollOffset.toLong() + appendedLines.toLong())
                .coerceAtMost(Int.MAX_VALUE.toLong())
                .toInt()
            pendingNewOutput = (pendingNewOutput + 1).coerceAtMost(Int.MAX_VALUE)
        } else {
            scrollOffset = 0
            pendingNewOutput = 0
        }
    }

    fun clear() {
        blocks.clear()
        scrollOffset = 0
        pendingNewOutput = 0
    }

    fun followTail() {
        scrollOffset = 0
        pendingNewOutput = 0
    }

    fun scrollUp(lines: Int = 4) {
        scrollOffset = (
            scrollOffset.toLong() + lines.coerceAtLeast(1).toLong()
        ).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }

    fun scrollDown(lines: Int = 4) {
        scrollOffset = (scrollOffset - lines.coerceAtLeast(1)).coerceAtLeast(0)
        if (scrollOffset == 0) pendingNewOutput = 0
    }

    fun visibleLines(width: Int, height: Int): List<String> {
        if (width <= 0 || height <= 0 || blocks.isEmpty()) return emptyList()
        lastRenderedWidth = width
        val lines = blocks.flatMap { AnsiLineWrapper.wrap(it, width) }
        val maximumOffset = (lines.size - height).coerceAtLeast(0)
        scrollOffset = scrollOffset.coerceIn(0, maximumOffset)
        val end = (lines.size - scrollOffset).coerceAtLeast(0)
        val start = (end - height).coerceAtLeast(0)
        return lines.subList(start, end)
    }
}
