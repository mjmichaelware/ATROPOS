/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.ui

import atropos.cli.ui.design.Role
import java.time.LocalTime

/**
 * The frames of the opening animation, as data.
 *
 * Atropos is the Fate who measures the thread and cuts it, so the opening is
 * a thread: it draws itself across the screen from the centre out, the
 * wordmark is woven out of it, and then the run states what it is. The motif
 * is not decoration for its own sake — it is the one image that says what this
 * engine claims to do, which is decide where something ends.
 *
 * Two deliberate choices shape the implementation:
 *
 * **It is a list of frames, not a loop.** Producing the frames and playing
 * them are separate jobs, so the sequence can be asserted on — that the last
 * frame holds the whole wordmark, that the workspace really appears in it,
 * that it runs for the length it claims — without a test ever sleeping. The
 * engine owns the clock.
 *
 * **The facts are facts.** Every line under the wordmark is read from the live
 * configuration, not written here. A startup screen that announced "verified"
 * or "ready" on a timer would be exactly the fake attestation AGENTS.md §0.6
 * forbids, and it would be the first thing an operator ever saw.
 */
class StartupSequence(
    private val theme: TerminalTheme,
    private val clock: () -> LocalTime = LocalTime::now
) {

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
        val greeting = greetingLine()
        val block = maxOf(span, lines.maxOfOrNull(TerminalText::cellWidth) ?: 0)

        val sequence = mutableListOf<List<String>>()

        // 1. The thread draws itself outward from the centre of the screen.
        var reach = 0
        val half = (safeWidth + 1) / 2
        while (reach < half) {
            reach = (reach + THREAD_STEP).coerceAtMost(half)
            sequence += compose(safeWidth, safeHeight, block, reach, blank(art), null, emptyList())
        }

        // 2. The wordmark is woven out of it.
        var revealed = 0
        while (revealed < span) {
            revealed = (revealed + REVEAL_STEP).coerceAtMost(span)
            sequence += compose(safeWidth, safeHeight, block, half, wipe(art, revealed), null, emptyList())
        }

        // 3. The greeting, then what this run actually is, a line at a time.
        // Each line dwells rather than flashing past. At one frame apiece the
        // whole block arrived and was replaced inside a fifth of a second --
        // long enough to see that something appeared, far too short to read
        // any of it.
        repeat(FACT_DWELL_FRAMES) {
            sequence += compose(safeWidth, safeHeight, block, half, wipe(art, span), greeting, emptyList())
        }
        lines.indices.forEach { index ->
            repeat(FACT_DWELL_FRAMES) {
                sequence += compose(safeWidth, safeHeight, block, half, wipe(art, span), greeting, lines.take(index + 1))
            }
        }

        // 4. Held, so the finished screen is something an operator reads rather
        //    than something that flickers past on the way to a prompt.
        val settled = sequence.last()
        repeat(HOLD_FRAMES) { sequence += settled }

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

    private fun blank(art: List<String>): List<String> = art.map { "" }

    /**
     * A greeting, because the first thing a tool says to a person should be
     * addressed to them.
     */
    private fun greetingLine(): String {
        val hour = clock().hour
        val greeting = when {
            hour < 12 -> "Good morning"
            hour < 18 -> "Good afternoon"
            else -> "Good evening"
        }
        return theme.strong(greeting) + theme.subdued(" — the thread is yours to cut.")
    }

    private fun factLines(facts: Facts): List<String> = listOf(
        label("version") + theme.strong(facts.version),
        label("provider") + theme.strong(facts.provider) +
            theme.subdued(" · ${facts.providerCount} configured"),
        label("territory") + theme.path(TerminalText.compactPath(facts.workspace)),
        label("") + theme.metadata("/ for commands · @ to attach a file")
    )

    private fun label(name: String): String =
        theme.subdued(name.padEnd(LABEL_CELLS))

    /**
     * Centres the block in the viewport, with the thread spanning the screen.
     *
     * [block] is passed in rather than measured, because a partially revealed
     * wordmark is narrower than a whole one and measuring each frame would make
     * the animation slide sideways as it drew.
     */
    private fun compose(
        width: Int,
        height: Int,
        block: Int,
        reach: Int,
        art: List<String>,
        greeting: String?,
        facts: List<String>
    ): List<String> {
        val thread = thread(width, reach)
        val body = buildList {
            add(thread)
            add("")
            addAll(art)
            add("")
            greeting?.let { add(it); add("") }
            addAll(facts)
            add("")
            add(thread)
        }

        val left = ((width - block.coerceAtMost(width)) / 2).coerceAtLeast(0)
        val top = ((height - body.size) / 2).coerceAtLeast(0)
        val pad = " ".repeat(left)

        return List(top) { "" } + body.map { line ->
            when {
                line.isEmpty() -> ""
                // The thread is already full-width and centred on the screen,
                // not on the text block, so it is placed rather than indented.
                line === thread -> line
                // Ellipsized, not wrapped: a fact that does not fit is one the
                // operator can read the start of, where a wrapped one would
                // push the wordmark off a short terminal to finish a hint.
                else -> TerminalText.ellipsize(pad + line, width)
            }
        }
    }

    /** A rule reaching [reach] cells either side of the screen's centre. */
    private fun thread(width: Int, reach: Int): String {
        if (reach <= 0) return ""
        val drawn = (reach * 2).coerceAtMost(width)
        val left = ((width - drawn) / 2).coerceAtLeast(0)
        return " ".repeat(left) + theme.paint(Role.ACCENT_FOCUS, THREAD.repeat(drawn))
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
        const val THREAD = "─"

        /** Seven glyphs of five cells, six single-cell gaps between them. */
        const val WORDMARK_CELLS = NAME.length * GLYPH_CELLS + (NAME.length - 1)

        /** Cells the thread gains per frame, each side of centre. */
        const val THREAD_STEP = 2

        /** Columns of wordmark drawn per frame. */
        const val REVEAL_STEP = 2

        /** How much of the leading edge is painted as the moving front. */
        const val EDGE_CELLS = 3

        /** Frames each fact line is left on screen before the next joins it. */
        const val FACT_DWELL_FRAMES = 6

        /** Frames the finished screen is held for before the prompt takes over. */
        const val HOLD_FRAMES = 40

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
