package atropos.cli.ui

import atropos.cli.config.ConfigurationManager
import atropos.cli.ui.design.ColorTier
import atropos.cli.ui.design.Health
import atropos.cli.ui.design.RunState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Acceptance gate 6: the CLI stays operable on narrow Termux widths.
 *
 * A cockpit that overflows its terminal wraps into unreadable rubble, so every
 * line is proved to fit at the four baseline widths tracked under
 * `docs/ui-parity/baseline/`. Colour is stripped before measuring because SGR
 * sequences occupy no cells.
 */
class DashboardRendererWidthTest {
    private val widths = listOf(40, 80, 120, 160)

    private fun renderer(): DashboardRenderer =
        DashboardRenderer(
            TerminalTheme(ConfigurationManager(), tierOverride = ColorTier.TRUECOLOR)
        )

    /** Deliberately over-long values: the cockpit must clip, not overflow. */
    private fun crowdedState(): DashboardRenderer.DashboardState =
        DashboardRenderer.DashboardState(
            answers = DashboardRenderer.SixAnswers(
                objective = DashboardRenderer.Answer(
                    "migrate the entire provider cascade to the new bounded agency gate " +
                        "and re-verify every downstream attestation envelope",
                    Health.PENDING
                ),
                doing = DashboardRenderer.Answer("running queue-20260729-121314-001", Health.VERIFIED),
                why = DashboardRenderer.Answer("97cff09c0f362337 [S0013] lines 46-48", Health.VERIFIED),
                progress = DashboardRenderer.Answer("3/11 complete · patch applied", Health.PENDING),
                next = DashboardRenderer.Answer(
                    "/agent queue show queue-20260729-121314-001 — repair failure",
                    Health.ERROR
                ),
                evidence = DashboardRenderer.Answer("7 linked · .atropos/agent/queue", Health.VERIFIED)
            ),
            runningWork = (1..12).map { index ->
                DashboardRenderer.WorkItem(
                    id = "queue-20260729-121314-%03d".format(index),
                    title = "a deliberately long task title that should be clipped rather than wrapped",
                    state = RunState.RETRYING,
                    detail = "patch generated",
                    attempt = 2,
                    maxAttempts = 5
                )
            },
            queuedItems = 9,
            failedItems = 2,
            provider = "groq",
            repository = RepositoryState(
                isRepository = true,
                branch = "claude/atropos-cli-ui-polish-yejl0p",
                changedFiles = 4,
                available = true
            ),
            heapUsedMb = 128,
            heapMaxMb = 4096
        )

    @Test
    fun no_line_overflows_any_baseline_width() {
        val renderer = renderer()
        widths.forEach { width ->
            renderer.render(crowdedState(), width).forEachIndexed { index, line ->
                val cells = TerminalText.stripAnsi(line).length
                assertTrue(
                    cells <= width,
                    "line $index overflowed ${width}col by ${cells - width}: ${TerminalText.stripAnsi(line)}"
                )
            }
        }
    }

    @Test
    fun all_six_answers_render_at_every_width_without_search() {
        val renderer = renderer()
        widths.forEach { width ->
            val plain = renderer.render(crowdedState(), width)
                .map { TerminalText.stripAnsi(it) }

            listOf("Objective", "Doing", "Why", "Progress", "Next", "Evidence").forEach { label ->
                assertTrue(
                    plain.any { it.startsWith(label) },
                    "answer '$label' missing at ${width}col"
                )
            }
        }
    }

    @Test
    fun hidden_queue_rows_are_declared_rather_than_silently_dropped() {
        val plain = renderer().render(crowdedState(), 40)
            .map { TerminalText.stripAnsi(it) }

        // 12 running items, 2 shown at COMPACT: the other 10 must be accounted for.
        assertTrue(
            plain.any { it.contains("+10 more") },
            "truncated work must be declared: $plain"
        )
    }

    @Test
    fun status_survives_without_colour() {
        // NO_COLOR / TERM=dumb: glyph and label must still carry the signal.
        val monochrome = DashboardRenderer(
            TerminalTheme(ConfigurationManager(), tierOverride = ColorTier.NONE)
        )
        val plain = monochrome.render(crowdedState(), 120)
            .map { TerminalText.stripAnsi(it) }

        assertEquals(
            plain,
            monochrome.render(crowdedState(), 120),
            "ColorTier.NONE must emit no SGR sequences at all"
        )
        assertTrue(
            plain.any { it.contains("retrying") },
            "the status label must survive monochrome rendering: $plain"
        )
    }
}
