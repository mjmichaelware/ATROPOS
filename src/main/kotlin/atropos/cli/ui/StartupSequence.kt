/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.ui

import atropos.cli.ui.design.Role

/**
 * The frames of the opening animation, as data.
 *
 * A wordmark wipes in left to right, then the facts about this run appear
 * beneath it one line at a time. Two deliberate choices shape it:
 *
 * **It is a list of frames, not a loop.** Producing the frames and playing
 * them are separate jobs, so the sequence can be asserted on — that the last
 * frame holds the whole wordmark, that the workspace really appears in it —
 * without a test ever sleeping. The engine owns the clock.
 *
 * **The facts are facts.** Every line under the wordmark is read from the
 * live configuration, not written here. A startup screen that announced
 * "verified" or "ready" on a timer would be exactly the fake attestation
 * AGENTS.md §0.6 forbids, and it would be the first thing an operator saw.
 */
class StartupSequence(private val theme: TerminalTheme) {

    /**
     * @param providerCount how many providers hold a key, which is a different
     *   number from how many exist. An operator who sees `1 configured` when
     *   they expected four has learned something in the first second.
     */
    data class Facts(
        val version: String,
        val provider: String,
        val providerCount: Int,
        val workspace: String
    )

    fun frames(width: Int, height: Int, facts: Facts): List<List<String>> {
        val safeWidth = width.coerceAtLeast(1)
        val safeHeight = height.coerceAtLeast(1)
        val art = if (safeWidth >= WORDMARK_CELLS + 2) wordmark() else listOf(spaced(NAME))
        val span = art.first().length
        val lines = factLines(facts)
        val block = maxOf(span, lines.maxOfOrNull(TerminalText::cellWidth) ?: 0)

        val sequence = mutableListOf<List<String>>()

        var revealed = 0
        while (revealed < span) {
            revealed = (revealed + REVEAL_STEP).coerceAtMost(span)
            sequence += compose(safeWidth, safeHeight, block, wipe(art, revealed), emptyList())
        }
        lines.indices.forEach { index ->
            sequence += compose(safeWidth, safeHeight, block, wipe(art, span), lines.take(index + 1))
        }

        return sequence
    }

    /** The settled screen, for callers that want the destination and no travel. */
    fun finalFrame(width: Int, height: Int, facts: Facts): List<String> =
        frames(width, height, facts).last()

    /**
     * The wordmark with everything past [columns] not yet drawn.
     *
     * The few columns at the leading edge are painted in the focus accent
     * rather than the brand colour, so the reveal reads as a moving edge
     * instead of a growing rectangle.
     */
    private fun wipe(art: List<String>, columns: Int): List<String> = art.map { row ->
        val visible = columns.coerceIn(0, row.length)
        val settled = row.take((visible - EDGE_CELLS).coerceAtLeast(0))
        val edge = row.substring(settled.length, visible)
        theme.brand(settled) + theme.paint(Role.ACCENT_FOCUS, edge)
    }

    private fun factLines(facts: Facts): List<String> = listOf(
        label("version") + theme.strong(facts.version),
        label("provider") + theme.strong(facts.provider) +
            theme.subdued(" · ${facts.providerCount} configured"),
        label("territory") + theme.path(TerminalText.compactPath(facts.workspace)),
        label("") + theme.metadata("/help for commands · @path to attach a file")
    )

    private fun label(name: String): String =
        theme.subdued(name.padEnd(LABEL_CELLS))

    /**
     * Centres the block in the viewport, with the art and the facts sharing one
     * left edge.
     *
     * [block] is passed in rather than measured, because a partially revealed
     * wordmark is narrower than a whole one and measuring each frame would
     * make the animation slide sideways as it drew.
     */
    private fun compose(
        width: Int,
        height: Int,
        block: Int,
        art: List<String>,
        facts: List<String>
    ): List<String> {
        val body = art + listOf("") + facts
        val left = ((width - block.coerceAtMost(width)) / 2).coerceAtLeast(0)
        val top = ((height - body.size) / 2).coerceAtLeast(0)
        val pad = " ".repeat(left)
        return List(top) { "" } +
            body.map { line ->
                // Ellipsized, not wrapped: a fact that does not fit is one the
                // operator can read the start of, where a wrapped one would
                // push the wordmark off a short terminal to finish a hint.
                if (line.isEmpty()) "" else TerminalText.ellipsize(pad + line, width)
            }
    }

    private fun wordmark(): List<String> = (0 until GLYPH_ROWS).map { row ->
        NAME.map { character -> GLYPHS.getValue(character)[row] }.joinToString(" ")
    }

    /** The fallback for a terminal too narrow for the block letters. */
    private fun spaced(value: String): String = value.toCharArray().joinToString(" ")

    private companion object {
        const val NAME = "ATROPOS"
        const val GLYPH_ROWS = 5
        const val GLYPH_CELLS = 5

        /** Seven glyphs of five cells, six single-cell gaps between them. */
        const val WORDMARK_CELLS = NAME.length * GLYPH_CELLS + (NAME.length - 1)

        /** Columns drawn per frame. Small enough to read as motion, large
         *  enough that the whole reveal is under half a second. */
        const val REVEAL_STEP = 3

        /** How much of the leading edge is painted as the moving front. */
        const val EDGE_CELLS = 3

        const val LABEL_CELLS = 11

        val GLYPHS: Map<Char, List<String>> = mapOf(
            'A' to listOf(" ███ ", "█   █", "█████", "█   █", "█   █"),
            'T' to listOf("█████", "  █  ", "  █  ", "  █  ", "  █  "),
            'R' to listOf("████ ", "█   █", "████ ", "█  █ ", "█   █"),
            'O' to listOf(" ███ ", "█   █", "█   █", "█   █", " ███ "),
            'P' to listOf("████ ", "█   █", "████ ", "█    ", "█    "),
            'S' to listOf(" ████", "█    ", " ███ ", "    █", "████ ")
        )
    }
}
