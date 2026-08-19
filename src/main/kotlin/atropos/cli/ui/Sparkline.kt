/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.ui

/**
 * A series, in as many cells as you can spare.
 *
 * The status bar carries tokens, cost and latency as single numbers, which
 * answer "what is it now" and hide "what has it been doing". A cost that
 * doubled in the last three calls and a cost that has been flat look identical
 * as a number and completely different as a shape, and the shape is the one an
 * operator can act on.
 *
 * Eight levels, because that is how many the block characters give, and no
 * more precision is claimed than the glyphs can carry.
 */
object Sparkline {

    private val LEVELS = charArrayOf('▁', '▂', '▃', '▄', '▅', '▆', '▇', '█')

    /**
     * @param values oldest first. Fewer values than [cells] draws short rather
     *   than stretching: a series of three points rendered across twenty cells
     *   invents seventeen readings nobody took.
     */
    fun render(values: List<Double>, cells: Int): String {
        if (cells <= 0 || values.isEmpty()) return ""
        val window = values.takeLast(cells)

        val low = window.min()
        val high = window.max()
        // A flat series is flat, not noise amplified to full scale. Scaling a
        // constant to the whole ramp would draw a dramatic shape from nothing
        // happening.
        if (high - low < FLAT_EPSILON) return LEVELS.first().toString().repeat(window.size)

        return window.joinToString("") { value ->
            val position = ((value - low) / (high - low) * (LEVELS.size - 1))
                .toInt()
                .coerceIn(0, LEVELS.lastIndex)
            LEVELS[position].toString()
        }
    }

    private const val FLAT_EPSILON = 1e-9
}
