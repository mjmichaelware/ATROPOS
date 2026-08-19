/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.ui

import atropos.cli.config.ConfigurationManager
import atropos.cli.ui.design.ColorTier
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The surfaces that make the engine legible rather than merely decorated.
 *
 * Each of these exists because a fact the engine already knew was reaching the
 * operator as nothing, or as prose they would have to be watching for.
 */
class VisualLanguageTest {

    private val theme = TerminalTheme(
        ConfigurationManager(envProvider = { null }, hasConsole = false),
        tierOverride = ColorTier.NONE
    )

    private fun plain(lines: List<String>) = lines.map(TerminalText::stripAnsi)

    // ----- the thread that measures the run --------------------------------

    @Test
    fun the_thread_grows_with_work_actually_finished() {
        val bar = ThreadProgress(theme)

        val quarter = TerminalText.stripAnsi(bar.render(1, 4, 40))
        val most = TerminalText.stripAnsi(bar.render(3, 4, 40))

        assertTrue(quarter.count { it == '━' } < most.count { it == '━' })
        assertTrue(quarter.contains("1/4"), quarter)
    }

    @Test
    fun an_unknown_total_is_drawn_as_unmeasured_rather_than_as_no_progress() {
        // "Nothing done yet" and "we do not know how much there is" are
        // different states and an empty bar claims the first.
        val rendered = TerminalText.stripAnsi(ThreadProgress(theme).render(0, 0, 40))

        assertTrue(rendered.contains("measuring"), rendered)
        assertFalse(rendered.contains("0/0"), rendered)
    }

    @Test
    fun the_thread_is_cut_when_the_run_ends() {
        val running = TerminalText.stripAnsi(ThreadProgress(theme).render(4, 4, 40, cut = false))
        val finished = TerminalText.stripAnsi(ThreadProgress(theme).render(4, 4, 40, cut = true))

        assertTrue(finished.contains("✂"), finished)
        assertEquals(running.length, finished.length, "cutting must not reflow the bar")
    }

    // ----- the session series ----------------------------------------------

    @Test
    fun a_flat_series_is_drawn_flat() {
        // Scaling a constant to the full ramp would draw a dramatic shape out
        // of nothing happening.
        assertEquals("▁▁▁▁", Sparkline.render(listOf(7.0, 7.0, 7.0, 7.0), 8))
    }

    @Test
    fun a_short_series_draws_short_rather_than_inventing_readings() {
        assertEquals(3, Sparkline.render(listOf(1.0, 5.0, 2.0), 20).length)
    }

    @Test
    fun the_series_rises_and_falls_with_its_values() {
        val rendered = Sparkline.render(listOf(1.0, 2.0, 3.0, 4.0), 8)

        assertEquals('▁', rendered.first())
        assertEquals('█', rendered.last())
    }

    // ----- the proof, as a mark --------------------------------------------

    @Test
    fun the_same_digest_always_draws_the_same_sigil() {
        val sigil = EvidenceSigil(theme)
        val digest = "d0ac9a71a62027551a85caa85a321ef3c07d78d8"

        assertEquals(plain(sigil.render(digest, true)), plain(sigil.render(digest, true)))
    }

    @Test
    fun different_digests_draw_different_sigils() {
        val sigil = EvidenceSigil(theme)

        assertTrue(
            plain(sigil.render("d0ac9a71a62027551a85caa85a321ef3c", true)) !=
                plain(sigil.render("88b38c9d0700a43bbbe0b9f86a1da6b1", true)),
            "two proofs drew the same seal"
        )
    }

    @Test
    fun a_digest_too_short_to_be_distinctive_is_refused() {
        // Four characters would collide constantly while looking exactly as
        // authoritative.
        assertTrue(EvidenceSigil(theme).render("abc1", true).isEmpty())
    }

    // ----- type that is actually bigger ------------------------------------

    @Test
    fun a_heading_is_drawn_at_twice_the_height() {
        val rendered = BlockType.render("ATROPOS", 80)

        assertEquals(BlockType.ROWS, rendered.size)
        assertTrue(rendered.all { it.isNotBlank() })
    }

    @Test
    fun text_that_will_not_fit_stays_plain_rather_than_overflowing() {
        assertEquals(listOf("ATROPOS"), BlockType.render("ATROPOS", 10))
    }

    // ----- the graph as the background -------------------------------------

    @Test
    fun the_wallpaper_shows_the_shape_of_the_work() {
        val states = List(20) { DagWallpaper.NodeState.DONE } +
            List(5) { DagWallpaper.NodeState.BLOCKED }

        val rendered = plain(DagWallpaper(theme).render(states, 60, 8))

        assertTrue(rendered.any { it.contains("█") }, "no finished nodes drawn")
        assertTrue(rendered.any { it.contains("░") }, "no blocked nodes drawn")
        assertTrue(rendered.last().contains("20 done"), rendered.last())
        assertTrue(rendered.last().contains("5 blocked"), rendered.last())
    }

    @Test
    fun a_graph_too_tall_for_the_space_says_what_it_left_out() {
        // A cropped picture of four hundred nodes silently omits the tail,
        // which is exactly where the unfinished work is.
        val rendered = plain(DagWallpaper(theme).render(List(400) { DagWallpaper.NodeState.READY }, 60, 5))

        assertTrue(rendered.last().contains("not shown"), rendered.last())
        assertTrue(rendered.last().contains("400 nodes"), rendered.last())
    }

    // ----- the cascade, made visible ---------------------------------------

    @Test
    fun the_relay_names_who_actually_answered() {
        val rendered = plain(
            ProviderRelay(theme).render(
                listOf(
                    ProviderRelay.Leg("anthropic", "rate limited"),
                    ProviderRelay.Leg("groq")
                ),
                60
            )
        )

        assertTrue(rendered.any { it.contains("rate limited") }, rendered.toString())
        assertTrue(rendered.last().contains("carried by") && rendered.last().contains("groq"))
    }

    @Test
    fun a_cascade_that_answered_nowhere_says_so() {
        val rendered = plain(
            ProviderRelay(theme).render(listOf(ProviderRelay.Leg("groq", "no key")), 60)
        )

        assertTrue(rendered.last().contains("no provider answered"), rendered.last())
    }

    // ----- confidence in the cloth -----------------------------------------

    @Test
    fun unresearched_work_is_drawn_as_looser_cloth() {
        val tapestry = ThreadTapestry(theme)

        val settled = plain(tapestry.render(80, 16, confidence = 1.0)).joinToString("")
        val unsettled = plain(tapestry.render(80, 16, confidence = 0.0)).joinToString("")

        assertTrue(
            unsettled.count { it == '│' } < settled.count { it == '│' },
            "loose cloth is not looser than settled cloth"
        )
    }

    @Test
    fun an_idle_screen_shows_settled_cloth_rather_than_implying_a_finished_run() {
        // Confidence defaults to settled, because a home screen with no run to
        // report on must not draw a picture that reads as a failing one.
        assertEquals(
            plain(ThreadTapestry(theme).render(80, 16)),
            plain(ThreadTapestry(theme).render(80, 16, confidence = 1.0))
        )
    }

    // ----- the first five minutes ------------------------------------------

    @Test
    fun the_guide_marks_only_what_is_actually_true_of_this_install() {
        val guide = FirstRunGuide(theme)

        val fresh = plain(guide.render(FirstRunGuide.Progress(false, false, false), 70))
        val ready = plain(guide.render(FirstRunGuide.Progress(true, true, true), 70))

        assertEquals(0, fresh.count { it.contains("✓") }, "a fresh install claimed finished steps")
        assertEquals(3, ready.count { it.contains("✓") }, ready.toString())
        assertTrue(ready.last().contains("All three are done"), ready.last())
    }

    @Test
    fun the_guide_never_overruns_the_screen() {
        listOf(30, 46, 80).forEach { width ->
            plain(FirstRunGuide(theme).render(FirstRunGuide.Progress(true, false, false), width))
                .forEach { line ->
                    assertTrue(
                        TerminalText.cellWidth(line) <= width,
                        "at $width cells the guide rendered ${TerminalText.cellWidth(line)}: '$line'"
                    )
                }
        }
    }

    // ----- an indicator that cannot lie ------------------------------------

    @Test
    fun a_spinner_with_nothing_behind_it_says_so() {
        var clock = 0L
        val sentinel = StallSentinel(quietAfterMillis = 10_000, silentAfterMillis = 60_000) { clock }

        sentinel.observedOutput()
        assertEquals(StallSentinel.Liveness.WORKING, sentinel.liveness())
        assertEquals("", sentinel.note(), "a label that is always there stops being read")

        clock += 15_000
        assertEquals(StallSentinel.Liveness.QUIET, sentinel.liveness())
        assertTrue(sentinel.note().contains("no output for 15s"), sentinel.note())

        clock += 60_000
        assertEquals(StallSentinel.Liveness.SILENT, sentinel.liveness())
        assertTrue(sentinel.note().contains("ctrl+c"), sentinel.note())
    }

    @Test
    fun output_arriving_makes_it_honest_again() {
        var clock = 0L
        val sentinel = StallSentinel(quietAfterMillis = 10_000, silentAfterMillis = 60_000) { clock }

        clock += 120_000
        assertEquals(StallSentinel.Liveness.SILENT, sentinel.liveness())

        sentinel.observedOutput()
        assertEquals(StallSentinel.Liveness.WORKING, sentinel.liveness())
    }
}
