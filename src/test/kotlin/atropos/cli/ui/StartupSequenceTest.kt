/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.ui

import atropos.cli.config.ConfigurationManager
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The opening animation, asserted without a clock.
 *
 * The sequence is a list of frames precisely so these tests can exist: a
 * startup that only ever ran against a real terminal on a real timer could
 * only be checked by watching it.
 */
class StartupSequenceTest {

    private val theme = TerminalTheme(ConfigurationManager(envProvider = { null }, hasConsole = false))
    private val sequence = StartupSequence(theme)
    private val facts = StartupSequence.Facts(
        version = "2.0.0-rc.1",
        provider = "groq",
        providerCount = 4,
        workspace = "/home/operator/ATROPOS"
    )

    private fun plain(frame: List<String>) = frame.map(TerminalText::stripAnsi)

    @Test
    fun the_wordmark_arrives_a_piece_at_a_time() {
        val frames = sequence.frames(80, 24, facts)

        assertTrue(frames.size > 4, "an animation of ${frames.size} frames is not an animation")

        val ink = frames.map { frame -> plain(frame).sumOf { line -> line.count { it == '█' } } }
        assertEquals(
            ink.sorted(), ink,
            "the wordmark must only ever gain ink; it went backwards"
        )
        assertTrue(ink.first() < ink.last(), "the first frame already held the whole wordmark")
    }

    @Test
    fun the_block_does_not_slide_while_it_draws() {
        // Centring each frame on its own measured width would re-centre the
        // wordmark on every step, so it would crawl sideways as it revealed
        // rather than wiping in place.
        val indents = sequence.frames(80, 24, facts).map { frame ->
            plain(frame).first { it.isNotBlank() }.takeWhile { it == ' ' }.length
        }

        assertEquals(1, indents.distinct().size, "the wordmark moved horizontally: $indents")
    }

    @Test
    fun the_settled_frame_states_what_this_run_actually_is() {
        val settled = plain(sequence.finalFrame(80, 24, facts)).joinToString("\n")

        assertTrue(settled.contains("2.0.0-rc.1"), "no version:\n$settled")
        assertTrue(settled.contains("groq"), "no provider:\n$settled")
        assertTrue(settled.contains("4 configured"), "no provider count:\n$settled")
        assertTrue(settled.contains("ATROPOS"), "no workspace:\n$settled")
    }

    @Test
    fun nothing_claims_a_verification_that_has_not_happened() {
        // AGENTS.md §0.6. A startup screen is the first thing an operator
        // reads, and a green word there on a timer is the cheapest possible
        // lie for the engine to tell.
        val settled = plain(sequence.finalFrame(80, 24, facts)).joinToString("\n").lowercase()

        listOf("verified", "attested", "healthy", "secure").forEach { claim ->
            assertTrue(claim !in settled, "the opening screen claims '$claim'")
        }
    }

    @Test
    fun a_narrow_terminal_gets_the_word_rather_than_the_block_letters() {
        val settled = plain(sequence.finalFrame(20, 24, facts))

        assertTrue(
            settled.none { it.contains('█') },
            "block letters were drawn into a terminal too narrow to hold them"
        )
        assertTrue(
            settled.any { it.contains("A T R O P O S") },
            "the narrow fallback dropped the wordmark entirely"
        )
    }

    @Test
    fun every_frame_fits_the_viewport_it_was_given() {
        sequence.frames(48, 20, facts).forEachIndexed { index, frame ->
            assertTrue(frame.size <= 20, "frame $index is ${frame.size} rows in a 20-row viewport")
            frame.forEach { line ->
                assertTrue(
                    TerminalText.cellWidth(line) <= 48,
                    "frame $index overflows 48 columns: ${TerminalText.cellWidth(line)}"
                )
            }
        }
    }
}
