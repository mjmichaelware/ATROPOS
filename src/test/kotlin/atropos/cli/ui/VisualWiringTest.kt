/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.ui

import atropos.cli.config.ConfigurationManager
import atropos.cli.input.CommandRegistry
import atropos.cli.session.QuotaSessionTracker
import atropos.cli.ui.design.ColorTier
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The surfaces, in the places they belong.
 *
 * Each of these was built and tested in isolation first, which proves it draws
 * correctly and proves nothing about whether anything draws it. A component
 * that is right and unreachable is indistinguishable from one that does not
 * exist.
 */
class VisualWiringTest {

    private val theme = TerminalTheme(
        ConfigurationManager(envProvider = { null }, hasConsole = false),
        tierOverride = ColorTier.NONE
    )

    private fun state(
        dag: List<DagWallpaper.NodeState> = emptyList(),
        confidence: Double = 1.0,
        costs: List<Double> = emptyList(),
        firstRun: FirstRunGuide.Progress? = null,
        repository: RepositoryState = RepositoryState.unknown()
    ) = SessionPresentationState(
        provider = "groq",
        mode = "ASK",
        workspace = "~/ATROPOS",
        commands = CommandRegistry.quickAccessCommands(),
        tokens = MetricValue.Known("1200"),
        cost = MetricValue.Known("$0.0100"),
        activeOperation = null,
        repository = repository,
        activeScreen = "Dashboard",
        dagNodeStates = dag,
        confidence = confidence,
        costHistory = costs,
        firstRun = firstRun
    )

    private fun landing(state: SessionPresentationState, width: Int = 70, height: Int = 30) =
        LandingRenderer(theme).render(state, width, height).map(TerminalText::stripAnsi)

    @Test
    fun the_home_screen_draws_cloth_when_there_is_no_run() {
        val rendered = landing(state()).joinToString("\n")

        assertTrue(rendered.contains("┏"), "no woven panel:\n$rendered")
        assertFalse(rendered.contains("nodes"), "a graph was claimed where none exists")
    }

    @Test
    fun the_home_screen_draws_the_graph_once_there_is_one() {
        // Same rows, same place, two meanings. A pattern occupying rows that
        // could have carried the run is a wasted screen.
        val states = List(12) { DagWallpaper.NodeState.DONE } +
            List(4) { DagWallpaper.NodeState.BLOCKED }

        val rendered = landing(state(dag = states)).joinToString("\n")

        assertTrue(rendered.contains("12 done"), rendered)
        assertTrue(rendered.contains("4 blocked"), rendered)
        assertFalse(rendered.contains("┏"), "cloth was drawn over a live run")
    }

    @Test
    fun a_fresh_install_is_told_what_to_do_instead_of_where_it_is() {
        val rendered = landing(state(firstRun = FirstRunGuide.Progress(false, false, false)))
            .joinToString("\n")

        assertTrue(rendered.contains("FIRST RUN"), rendered)
        // Instead of, not beneath: a screen with both is a screen where
        // neither is the answer.
        assertFalse(rendered.contains("START HERE"), rendered)
    }

    @Test
    fun an_established_install_gets_the_ordinary_home_screen() {
        val rendered = landing(state()).joinToString("\n")

        assertTrue(rendered.contains("START HERE"), rendered)
        assertFalse(rendered.contains("FIRST RUN"), rendered)
    }

    @Test
    fun the_footer_carries_the_shape_of_the_spend_once_there_is_one() {
        val bar = StatusBarRenderer(theme)

        val quiet = TerminalText.stripAnsi(bar.footer(state(), 120))
        val spending = TerminalText.stripAnsi(
            bar.footer(state(costs = listOf(0.001, 0.004, 0.002, 0.009)), 120)
        )

        assertFalse(quiet.contains("▁"), "a flat line was drawn from no readings: $quiet")
        assertTrue(spending.any { it in "▁▂▃▄▅▆▇█" }, spending)
    }

    @Test
    fun the_footer_keeps_active_provider_and_repository_dirty_state_visible() {
        val rendered = TerminalText.stripAnsi(
            StatusBarRenderer(theme).footer(
                state(repository = RepositoryState(true, "main", 2, true)),
                140
            )
        )

        assertTrue(rendered.contains("groq"), rendered)
        assertTrue(rendered.contains("main"), rendered)
        assertTrue(rendered.contains("!"), rendered)
    }

    @Test
    fun the_session_tracker_records_what_each_request_cost() {
        val tracker = QuotaSessionTracker()

        tracker.recordPrompt("hello there", inputUsdPerToken = 0.001)
        tracker.recordPrompt("a longer prompt than the first one", inputUsdPerToken = 0.001)

        assertEquals(2, tracker.costHistory().size)
        assertTrue(tracker.costHistory().last() > tracker.costHistory().first())
    }

    @Test
    fun the_cost_history_is_bounded_so_a_long_session_cannot_grow_forever() {
        val tracker = QuotaSessionTracker()
        repeat(500) { tracker.recordPrompt("prompt $it", inputUsdPerToken = 0.001) }

        assertTrue(tracker.costHistory().size <= 64, "history grew to ${tracker.costHistory().size}")
    }

    @Test
    fun the_indicator_reports_progress_from_the_narration_the_pipeline_emits() {
        // `PipelineNarrator.item` writes `[3/390] …` by contract, and that
        // number was previously used only as prose.
        val pattern = Regex("""^\[(\d+)/(\d+)]""")
        val line = "[3/390] atom-0003 — an obligation"

        val match = pattern.find(line)
        assertEquals("3", match?.groupValues?.get(1))
        assertEquals("390", match?.groupValues?.get(2))
    }
}
