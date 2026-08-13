/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.observability

/**
 * A run as Markdown, for a human to read or paste somewhere.
 *
 * Source Doc 3 §5.2 names Markdown and JSON as separate small files, and the
 * separation is not ceremony: they are read by different things. This one is
 * ordered for a person opening it cold — what ran, what broke, then the detail
 * — and puts failures before successes because an export is usually opened
 * because something went wrong.
 *
 * Pure. Given the same [RunExport] it produces the same bytes, which is what
 * makes the event-determinism metric in Source Doc 3 §4.1 checkable at all. No
 * clock is read here; the export's timestamp came from the export.
 */
class MarkdownExporter(private val cards: CardRenderer = CardRenderer()) {

    fun export(run: RunExport): String = buildString {
        appendLine("# Run ${run.runId}")
        appendLine()
        appendSummary(run)
        appendFailures(run)
        appendRequirements(run)
        appendCards(run)
        appendTrace(run)
    }.trimEnd() + "\n"

    private fun StringBuilder.appendSummary(run: RunExport) {
        appendLine("## Summary")
        appendLine()
        appendLine("| | |")
        appendLine("|---|---|")
        appendLine("| Exported | ${run.exportedAt} |")
        appendLine("| Events | ${run.eventCount} |")
        appendLine("| Cards | ${run.cardCount} |")
        run.span?.let { (first, last) -> appendLine("| Span | $first → $last |") }
        appendLine("| Roles | ${run.roles().joinToString(", ") { it.canonical }} |")
        appendLine("| Providers | ${run.providers().ifEmpty { listOf("none") }.joinToString(", ")} |")
        appendLine("| Trace completeness | ${percent(run.traceCompleteness)} |")
        appendLine()
    }

    /**
     * Failures first, and named.
     *
     * An export with fifty cards and one failing command should not require
     * reading fifty cards. The full card still appears in its place below; this
     * is an index into it, not a copy, so there is one authoritative rendering
     * of each card and no chance of the two drifting.
     */
    private fun StringBuilder.appendFailures(run: RunExport) {
        val failures = run.failures()
        if (failures.isEmpty()) return
        appendLine("## Failures")
        appendLine()
        failures.forEach { card ->
            append("- **").append(card.kind.label).append(" #").append(card.sequence).append("** — ")
            appendLine(card.title)
        }
        appendLine()
    }

    /**
     * Which requirements this run served.
     *
     * The source-to-code trace the Blueprint asks for, from the run's side: an
     * export that cannot say which requirements it advanced is a log, not
     * evidence.
     */
    private fun StringBuilder.appendRequirements(run: RunExport) {
        val requirements = run.requirements()
        if (requirements.isEmpty()) return
        appendLine("## Requirements touched")
        appendLine()
        requirements.forEach { appendLine("- `$it`") }
        appendLine()
    }

    private fun StringBuilder.appendCards(run: RunExport) {
        if (run.cards.isEmpty()) return
        appendLine("## Output")
        appendLine()
        run.cards.forEach { card ->
            appendLine(cards.renderMarkdown(card))
            appendLine()
        }
    }

    /**
     * The full event trace, last.
     *
     * Included because §5.2 says a *full* run, and placed last because almost
     * nobody reads it — but the one time somebody needs it, an export that
     * omitted it is worthless and there is no way to recover it after the fact.
     * Incomplete events are marked rather than hidden.
     */
    private fun StringBuilder.appendTrace(run: RunExport) {
        appendLine("## Trace")
        appendLine()
        appendLine("| # | Time | Role | State | Category | Requirement | Payload |")
        appendLine("|---|---|---|---|---|---|---|")
        run.events.forEach { event ->
            append("| ").append(event.sequence)
            append(" | ").append(event.timestamp.toString().substringAfter('T').substringBefore('.'))
            append(" | ").append(event.role.canonical)
            append(" | ").append(event.state.label)
            append(" | ").append(event.category.name)
            append(" | ").append(event.requirement?.let { "`$it`" } ?: "—")
            append(" | ").append(cell(event.payload))
            appendLine(" |")
        }
        appendLine()
        val incomplete = run.incompleteEvents()
        if (incomplete.isNotEmpty()) {
            appendLine("${incomplete.size} of ${run.eventCount} events are missing required provenance:")
            appendLine()
            incomplete.take(INCOMPLETE_LISTED).forEach { event ->
                appendLine("- `#${event.sequence}` missing ${event.missingProvenance().joinToString(", ")}")
            }
            if (incomplete.size > INCOMPLETE_LISTED) {
                appendLine("- … and ${incomplete.size - INCOMPLETE_LISTED} more")
            }
        }
    }

    /** A payload rendered safely inside a table cell. */
    private fun cell(payload: String): String {
        val single = payload.replace("\n", " ").replace("|", "\\|")
        return if (single.length <= CELL_WIDTH) single else single.take(CELL_WIDTH - 1) + "…"
    }

    private fun percent(value: Double): String = "${(value * 100).toInt()}%"

    private companion object {
        const val CELL_WIDTH = 80
        const val INCOMPLETE_LISTED = 20
    }
}
