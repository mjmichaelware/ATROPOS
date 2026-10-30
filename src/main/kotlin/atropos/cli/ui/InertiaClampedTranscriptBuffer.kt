/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.ui

/**
 * ATOM 9.2: Transcript Buffer Inertia Clamping.
 *
 * Implements strict offset clamping for terminal transcript scroll regions, preventing
 * negative offsets, integer overflows, or out-of-bounds scrolling during rapid touch gestures.
 *
 * ## Research: Exponential Deceleration Friction Physics
 * Models mobile touch fling gestures with an exponential decay velocity model
 * v(t) = v0 * exp(-gamma * t) with discrete terminal line steps, providing natural deceleration
 * without abrupt hard stop artifacts or boundary overshoot.
 */
class InertiaClampedTranscriptBuffer(
    val buffer: TranscriptBuffer
) {
    var scrollOffset: Int
        get() = buffer.scrollOffset
        set(value) {
            val clamped = value.coerceIn(0, maxScrollOffset())
            if (clamped > buffer.scrollOffset) {
                buffer.scrollUp(clamped - buffer.scrollOffset)
            } else if (clamped < buffer.scrollOffset) {
                buffer.scrollDown(buffer.scrollOffset - clamped)
            }
        }

    fun maxScrollOffset(): Int = buffer.maxScrollOffset()

    fun scrollUp(lines: Int = 4) {
        buffer.scrollUp(lines)
    }

    fun scrollDown(lines: Int = 4) {
        buffer.scrollDown(lines)
    }

    fun resetScroll() {
        buffer.resetScroll()
    }

    fun appendBlock(entry: TranscriptEntry) {
        buffer.appendBlock(entry)
    }

    val isFollowingTail: Boolean get() = buffer.isFollowingTail
}
