/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.ui

/**
 * The frames of the working indicator.
 *
 * A single braille character rotating in place was technically an animation
 * and practically invisible: one cell of movement at the end of a line, on a
 * phone, while a long run scrolled past. The question it exists to answer --
 * "is this alive or has it hung?" -- has to be answerable at a glance and
 * without hunting for it.
 *
 * So it is a travelling pulse across a bar of cells. The brightness falls off
 * either side of a moving centre, which reads as motion even in peripheral
 * vision, holds attention without demanding it, and stays legible on a
 * monochrome terminal because the ramp is glyph height rather than colour.
 *
 * Pure frames, scheduling stays [SpinnerEngine]'s concern.
 */
class AnimatedThinkingBuffer(
    private val frames: List<String> = DEFAULT_FRAMES
) {
    init {
        require(frames.isNotEmpty() && frames.all(String::isNotBlank)) { "thinking frames are required" }
    }

    fun frame(index: Int): String = frames[index.floorMod(frames.size)]

    fun render(index: Int, message: String): String {
        require(message.isNotBlank()) { "thinking message is required" }
        return "${frame(index)}  $message"
    }

    fun sequence(count: Int, message: String): List<String> {
        require(count >= 0) { "thinking frame count cannot be negative" }
        return (0 until count).map { render(it, message) }
    }

    private fun Int.floorMod(modulus: Int): Int = ((this % modulus) + modulus) % modulus

    private companion object {
        const val CELLS = 9

        /** Tall to short; the pulse is the tallest cell. */
        val RAMP = listOf('█', '▇', '▅', '▃', '▂', '▁')

        /**
         * One frame per position of the pulse, out and back.
         *
         * Out and back rather than wrapping, because a pulse that jumps from
         * the right edge to the left reads as a glitch rather than as travel.
         */
        val DEFAULT_FRAMES: List<String> = buildList {
            val positions = (0 until CELLS) + (CELLS - 2 downTo 1)
            positions.forEach { centre ->
                add(
                    (0 until CELLS).joinToString("") { cell ->
                        val distance = kotlin.math.abs(cell - centre)
                        RAMP.getOrElse(distance) { RAMP.last() }.toString()
                    }
                )
            }
        }
    }
}
