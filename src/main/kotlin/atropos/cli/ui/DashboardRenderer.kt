/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.ui

import atropos.cli.ui.design.Breakpoint
import atropos.cli.ui.design.Health
import atropos.cli.ui.design.Role
import atropos.cli.ui.design.RunState
import atropos.cli.ui.design.Spacing
import atropos.cli.ui.design.Surface

/**
 * ATROPOS HOE (Human Operating Environment) Home cockpit.
 *
 * Source Document 4 §0.1: the interface shall always answer six questions
 * without requiring the user to search. This renderer draws those answers and
 * nothing else claims to know them.
 *
 * It is a pure renderer: it never reads state. [HomeStateProvider] captures the
 * state so that what appears here is what the runtime actually reported,
 * including the reports of not knowing.
 */
class DashboardRenderer(
    private val theme: TerminalTheme
) {
    /** A single answer plus its non-fabricated confidence. */
    data class Answer(
        val value: String,
        val health: Health = Health.UNKNOWN
    ) {
        companion object {
            val UNKNOWN = Answer("unknown", Health.UNKNOWN)
        }
    }

    /** The six continuous answers of Source Document 4 §0.1, in order. */
    data class SixAnswers(
        val objective: Answer = Answer.UNKNOWN,
        val doing: Answer = Answer.UNKNOWN,
        val why: Answer = Answer.UNKNOWN,
        val progress: Answer = Answer.UNKNOWN,
        val next: Answer = Answer.UNKNOWN,
        val evidence: Answer = Answer.UNKNOWN
    )

    /**
     * [state] is the Section A vocabulary rather than a raw runtime enum name,
     * so the queue reads as user progress and renders through the three
     * redundant channels [Surface.runState] guarantees.
     */
    data class WorkItem(
        val id: String,
        val title: String,
        val state: RunState,
        val detail: String,
        val attempt: Int? = null,
        val maxAttempts: Int? = null
    )

    data class DashboardState(
        val answers: SixAnswers = SixAnswers(),
        val runningWork: List<WorkItem> = emptyList(),
        val queuedItems: Int = 0,
        val failedItems: Int = 0,
        /** False when the durable queue could not be read — not the same as empty. */
        val queueReadable: Boolean = true,
        val provider: String = "unknown",
        val repository: RepositoryState = RepositoryState.unknown(),
        val heapUsedMb: Long = 0,
        val heapMaxMb: Long = 0
    )

    fun render(state: DashboardState, width: Int): List<String> {
        val safeWidth = width.coerceIn(Spacing.MIN_WIDTH, 200)
        val bp = Breakpoint.of(safeWidth)
        val output = mutableListOf<String>()

        output += theme.surface.sectionHeading("HOME", safeWidth, Role.BRAND)
        output += renderSixAnswers(state.answers, safeWidth, bp)

        if (state.runningWork.isNotEmpty()) {
            output += ""
            output += theme.surface.sectionHeading("WORK", safeWidth, Role.BRAND)
            output += renderWork(state.runningWork, safeWidth, bp)
        }

        output += ""
        output += theme.surface.sectionHeading("SYSTEM", safeWidth, Role.BRAND)
        output += renderSystem(state, safeWidth)

        return output
    }

    /**
     * The six questions are labelled with their own words so the operator can
     * map answer to question without the spec in hand. Wide terminals also get
     * the full question as a subdued suffix — progressive disclosure by width,
     * never by hiding an answer.
     */
    private fun renderSixAnswers(
        answers: SixAnswers,
        width: Int,
        bp: Breakpoint
    ): List<String> {
        val questions = bp >= Breakpoint.WIDE
        return listOf(
            answerRow("Objective", "what am I trying to accomplish", answers.objective, width, questions),
            answerRow("Doing", "what is ATROPOS doing", answers.doing, width, questions),
            answerRow("Why", "why is it doing that", answers.why, width, questions),
            answerRow("Progress", "how far along is it", answers.progress, width, questions),
            answerRow("Next", "what should I do next", answers.next, width, questions),
            answerRow("Evidence", "can I inspect the evidence", answers.evidence, width, questions)
        )
    }

    private fun answerRow(
        label: String,
        question: String,
        answer: Answer,
        width: Int,
        withQuestion: Boolean
    ): String {
        // The answer text is always self-describing, so the health colour is a
        // second channel rather than the only one (§9.2, colour-independent).
        val painted = theme.paint(answer.health.role, answer.value)
        val suffix = if (withQuestion) " " + theme.subdued("· $question") else ""
        return theme.surface.row(label, painted + suffix, width)
    }

    private fun renderWork(work: List<WorkItem>, width: Int, bp: Breakpoint): List<String> {
        val shown = work.take(bp.maxWorkItems())
        val output = shown.map { item ->
            val status = theme.surface.runState(item.state, item.attempt, item.maxAttempts)
            theme.surface.row(
                TerminalText.ellipsize(item.id, Spacing.LABEL_WIDTH),
                "$status ${theme.subdued(item.detail)} ${item.title}",
                width
            )
        }.toMutableList()

        // Never silently truncate the queue: a hidden item is an unanswered
        // question about what ATROPOS is doing.
        val hidden = work.size - shown.size
        if (hidden > 0) {
            output += theme.surface.row("", theme.subdued("+$hidden more · /agent queue list"), width)
        }
        return output
    }

    private fun renderSystem(state: DashboardState, width: Int): List<String> {
        val output = mutableListOf<String>()

        output += theme.surface.statusRow("Queue", queueSummary(state), queueHealth(state), width)

        // Provider identity is known; provider health is not measured here, so
        // it is not claimed here.
        output += theme.surface.row("Provider", state.provider, width)

        val repo = state.repository
        val repoText = when {
            !repo.available -> "unavailable"
            !repo.isRepository -> "not a repository"
            else -> listOfNotNull(
                repo.branch,
                repo.changedFiles?.let { if (it == 0) "clean" else "$it changed" }
            ).joinToString(" · ").ifBlank { "unknown" }
        }
        output += theme.surface.statusRow(
            "Repository",
            repoText,
            if (repo.available) Health.ofNullable(repo.clean) else Health.UNKNOWN,
            width
        )

        // Megabytes, not a percentage: real usage rounds to 0% and an operator
        // reasonably reads a zero as a broken probe.
        output += theme.surface.row(
            "Heap",
            if (state.heapMaxMb > 0) "${state.heapUsedMb}/${state.heapMaxMb} MB" else "unknown",
            width
        )
        return output
    }

    private fun queueSummary(state: DashboardState): String {
        if (!state.queueReadable) return "unreadable"
        return "${state.runningWork.size} open · ${state.queuedItems} queued · ${state.failedItems} failed"
    }

    private fun queueHealth(state: DashboardState): Health = when {
        !state.queueReadable -> Health.ERROR
        state.failedItems > 0 -> Health.ERROR
        state.runningWork.isNotEmpty() -> Health.PENDING
        else -> Health.VERIFIED
    }

    /** Termux-narrow terminals show fewer rows; the overflow count still shows. */
    private fun Breakpoint.maxWorkItems(): Int = when (this) {
        Breakpoint.COMPACT -> 2
        Breakpoint.MEDIUM -> 4
        Breakpoint.WIDE -> 6
        Breakpoint.ULTRA -> 8
    }
}
