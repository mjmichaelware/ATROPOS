/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.ui

sealed interface TranscriptEntry {
    data class Text(val value: String) : TranscriptEntry
    data class Disclosure(val row: DisclosureRow) : TranscriptEntry
}

/**
 * ATOM 9.2: Bounded Inertial Scrolling and Spin Termination.
 *
 * Transcript buffer maintaining rendered entries, scroll offset clamping, and tail following.
 * Clamps scroll offsets strictly between zero and the maximum buffered output lines.
 * Terminates touch fling inertia cleanly when reaching boundary edges, avoiding infinite
 * scroll loops or unresponsive touch states.
 *
 * ## Further Research Directive: Decayed Velocity Physics Models
 * Research decayed velocity physics models for touch scroll fling gestures to ensure
 * natural deceleration curves in software-rendered terminal viewports.
 */
class TranscriptBuffer(
    private val maximumBlocks: Int = 600
) {
    private val blocks = ArrayDeque<TranscriptEntry>()
    var scrollOffset = 0
        private set
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

    fun maxScrollOffset(): Int = (blocks.size * 10).coerceAtLeast(0)

    fun resetScroll() {
        followTail()
    }

    fun appendBlock(entry: TranscriptEntry) {
        append(entry)
    }

    fun asInertiaClamped(): InertiaClampedTranscriptBuffer = InertiaClampedTranscriptBuffer(this)

    fun setRenderedWidth(width: Int) {
        if (width > 0) lastRenderedWidth = width
    }

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

    private var inertialVelocity: Double = 0.0

    fun terminateInertia() {
        inertialVelocity = 0.0
    }

    fun applyInertialScroll(velocityDelta: Double) {
        inertialVelocity = (inertialVelocity + velocityDelta).coerceIn(-100.0, 100.0)
        val deltaLines = inertialVelocity.toInt()
        if (deltaLines > 0) {
            scrollUp(deltaLines)
            if (scrollOffset >= maxScrollOffset()) terminateInertia()
        } else if (deltaLines < 0) {
            scrollDown(-deltaLines)
            if (scrollOffset <= 0) terminateInertia()
        }
        // Exponential decay model: 0.85 per step
        inertialVelocity *= 0.85
        if (kotlin.math.abs(inertialVelocity) < 0.1) terminateInertia()
    }

    fun followTail() {
        scrollOffset = 0
        pendingNewOutput = 0
    }

    fun scrollUp(lines: Int = 4) {
        val maxOffset = maxScrollOffset()
        scrollOffset = (scrollOffset + lines.coerceAtLeast(1)).coerceIn(0, maxOffset)
    }

    fun scrollDown(lines: Int = 4) {
        scrollOffset = (scrollOffset - lines.coerceAtLeast(1)).coerceIn(0, maxScrollOffset())
        if (scrollOffset == 0) pendingNewOutput = 0
    }

    /**
     * Returns the entries for rendering by [TranscriptRenderer].
     */
    fun entries(): List<TranscriptEntry> = blocks.toList()

    fun getEntry(index: Int): TranscriptEntry? = blocks.getOrNull(index)

    fun size(): Int = blocks.size

    fun visibleLines(width: Int, height: Int): List<String> {
        val allLines = blocks.map { entry ->
            when (entry) {
                is TranscriptEntry.Text -> {
                    val wrapped = AnsiLineWrapper.wrap(entry.value, width)
                    if (wrapped.isEmpty()) listOf("") else wrapped
                }
                is TranscriptEntry.Disclosure -> listOf(entry.row.summary)
            }
        }.flatten()

        val start = (size() - scrollOffset - height).coerceAtLeast(0)
        return allLines.subList(start, (start + height).coerceAtMost(allLines.size))
    }
}
