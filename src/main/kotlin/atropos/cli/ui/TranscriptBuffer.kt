/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.ui

sealed interface TranscriptEntry {
    data class Text(val value: String) : TranscriptEntry
    data class Disclosure(val row: DisclosureRow) : TranscriptEntry
}

class TranscriptBuffer(
    private val maximumBlocks: Int = 600
) {
    private val blocks = ArrayDeque<TranscriptEntry>()
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

    val currentScrollOffset: Int
        get() = scrollOffset

    fun append(value: String) {
        append(TranscriptEntry.Text(value))
    }

    fun append(entry: TranscriptEntry) {
        val wasAwayFromTail = scrollOffset > 0
        val appendedLines = when (entry) {
            is TranscriptEntry.Text -> if (lastRenderedWidth > 0) {
                AnsiLineWrapper.wrap(entry.value, lastRenderedWidth).size
            } else 0
            is TranscriptEntry.Disclosure -> 1 // Summary line only when collapsed
        }
        blocks.addLast(entry)
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

    fun appendDisclosure(kind: DisclosureKind, summary: String, detail: String) {
        append(TranscriptEntry.Disclosure(DisclosureRow(kind, summary, detail)))
    }

    fun toggleDisclosure(index: Int) {
        val entries = blocks.toList()
        if (index in blocks.indices) {
            val entry = entries[index]
            if (entry is TranscriptEntry.Disclosure) {
                blocks[index] = TranscriptEntry.Disclosure(entry.row.toggle())
            }
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

    /**
     * Returns the entries for rendering by [TranscriptRenderer].
     */
    fun entries(): List<TranscriptEntry> = blocks.toList()

    fun getEntry(index: Int): TranscriptEntry? = blocks.getOrNull(index)

    fun size(): Int = blocks.size
}
