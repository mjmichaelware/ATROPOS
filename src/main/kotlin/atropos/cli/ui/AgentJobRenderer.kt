/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.ui

import atropos.core.security.RedactionFilter

class AgentJobRenderer(
    private val theme: TerminalTheme,
    private val redactionFilter: RedactionFilter = RedactionFilter()
) {
    fun renderRunSummary(job: AgentJobSummary, width: Int): List<String> {
        val job = job.redact(redactionFilter)
        val safeWidth = width.coerceAtLeast(28)
        val out = mutableListOf<String>()
        out += divider("AGENT RUN", safeWidth)
        out += row("job", job.id, safeWidth)
        out += row("status", statusBadge(job.status), safeWidth)
        out += row("task", TerminalText.ellipsize(job.task, valueWidth(safeWidth)), safeWidth)
        out += row("provider", job.provider ?: theme.subdued("pending"), safeWidth)
        out += row("patch", job.patchId ?: theme.subdued("none yet"), safeWidth)
        out += row("verification", job.verificationId ?: theme.subdued("none yet"), safeWidth)
        job.smokeCommand?.takeIf { it.isNotBlank() }?.let {
            out += row("smoke cmd", TerminalText.ellipsize(it, valueWidth(safeWidth)), safeWidth)
        }
        out += row("smoke", job.smokeSummary ?: theme.subdued("not run"), safeWidth)
        job.finalReport?.takeIf { it.isNotBlank() }?.let {
            out += row("final", TerminalText.ellipsize(it, valueWidth(safeWidth)), safeWidth)
        }
        job.commitProposal?.takeIf { it.isNotBlank() }?.let {
            out += row("commit", TerminalText.ellipsize(it, valueWidth(safeWidth)), safeWidth)
        }
        job.note?.takeIf { it.isNotBlank() }?.let {
            out += row("note", TerminalText.ellipsize(it, valueWidth(safeWidth)), safeWidth)
        }
        out += row("next", theme.code(nextCommand(job)), safeWidth)
        return out.map { TerminalText.ellipsize(it, safeWidth) }
    }

    fun renderJobsList(jobs: List<AgentJobSummary>, width: Int): List<String> {
        val jobs = jobs.map { it.redact(redactionFilter) }
        val safeWidth = width.coerceAtLeast(28)
        val out = mutableListOf<String>()
        out += divider("AGENT JOBS", safeWidth)

        if (jobs.isEmpty()) {
            out += theme.subdued("no jobs yet · /agent run <task>")
            return out.map { TerminalText.ellipsize(it, safeWidth) }
        }

        out += when {
            safeWidth >= 100 -> wideTable(jobs, safeWidth)
            safeWidth >= 64 -> compactTable(jobs, safeWidth)
            else -> stackedList(jobs, safeWidth)
        }

        out += theme.metadata("${jobs.size} job" + if (jobs.size == 1) "" else "s" + " · /agent job <id> for detail")
        return out.map { TerminalText.ellipsize(it, safeWidth) }
    }

    fun renderJobDetail(job: AgentJobSummary, timeline: List<AgentJobEvent> = emptyList(), width: Int): List<String> {
        val job = job.redact(redactionFilter)
        val timeline = timeline.map { it.redact(redactionFilter) }
        val safeWidth = width.coerceAtLeast(28)
        val out = mutableListOf<String>()
        out += divider("AGENT JOB ${job.id}", safeWidth)
        out += row("status", statusBadge(job.status), safeWidth)
        out += row("task", TerminalText.ellipsize(job.task, valueWidth(safeWidth)), safeWidth)
        out += row("provider", job.provider ?: theme.subdued("pending"), safeWidth)
        out += row("patch", job.patchId ?: theme.subdued("none yet"), safeWidth)
        out += row("verification", job.verificationId ?: theme.subdued("none yet"), safeWidth)
        job.smokeCommand?.takeIf { it.isNotBlank() }?.let {
            out += row("smoke cmd", TerminalText.ellipsize(it, valueWidth(safeWidth)), safeWidth)
        }
        out += row("smoke", job.smokeSummary ?: theme.subdued("not run"), safeWidth)
        job.finalReport?.takeIf { it.isNotBlank() }?.let {
            out += row("final", TerminalText.ellipsize(it, valueWidth(safeWidth)), safeWidth)
        }
        job.commitProposal?.takeIf { it.isNotBlank() }?.let {
            out += row("commit", TerminalText.ellipsize(it, valueWidth(safeWidth)), safeWidth)
        }
        out += row("changed", job.changedPathsCount?.let { "$it paths" } ?: "unknown", safeWidth)
        out += row("started", job.startedAt ?: theme.subdued("unknown"), safeWidth)
        out += row("updated", job.updatedAt ?: theme.subdued("unknown"), safeWidth)
        job.contextExportPath?.takeIf { it.isNotBlank() }?.let {
            out += row("context", TerminalText.ellipsize(it, valueWidth(safeWidth)), safeWidth)
        }
        job.note?.takeIf { it.isNotBlank() }?.let {
            out += row("note", TerminalText.ellipsize(it, valueWidth(safeWidth)), safeWidth)
        }

        if (timeline.isNotEmpty()) {
            out += ""
            out += theme.brand("timeline")
            timeline.forEach { event ->
                val line = theme.metadata(TerminalText.padEnd(event.at, 11)) + " " +
                    statusBadge(event.status) +
                    (event.note?.takeIf { it.isNotBlank() }?.let { " " + theme.subdued(it) } ?: "")
                out += TerminalText.ellipsize(line, safeWidth)
            }
        }

        out += row("next", theme.code(nextCommand(job)), safeWidth)
        return out.map { TerminalText.ellipsize(it, safeWidth) }
    }

    private fun nextCommand(job: AgentJobSummary): String =
        job.nextSuggestedCommand?.takeIf { it.isNotBlank() } ?: when (job.status) {
            AgentJobStatus.QUEUED,
            AgentJobStatus.PLANNING,
            AgentJobStatus.PATCHING,
            AgentJobStatus.APPLYING,
            AgentJobStatus.VERIFYING,
            AgentJobStatus.REPAIRING -> "/agent job ${job.id}  (check progress)"
            AgentJobStatus.PASSED -> "git status --short  (review changes)"
            AgentJobStatus.FAILED -> "/agent job ${job.id}  (see failure detail)"
            AgentJobStatus.REFUSED -> "/agent job ${job.id}  (see refusal reason)"
        }

    private fun statusBadge(status: AgentJobStatus): String {
        val paint: (String) -> String = when (status) {
            AgentJobStatus.PASSED -> theme::success
            AgentJobStatus.FAILED, AgentJobStatus.REFUSED -> theme::error
            else -> theme::warning
        }
        return paint("[${status.label}]")
    }

    private fun divider(title: String, width: Int): String =
        TerminalText.ellipsize(theme.brand("── $title ──"), width)

    private fun row(label: String, value: String, width: Int): String {
        val prefix = theme.metadata(TerminalText.padEnd(label, 9)) + " "
        return TerminalText.ellipsize(prefix + value, width)
    }

    private fun valueWidth(width: Int): Int = (width - 10).coerceAtLeast(8)

    private fun wideTable(jobs: List<AgentJobSummary>, width: Int): List<String> {
        val idWidth = 14
        val statusWidth = 11
        val providerWidth = 12
        val updatedWidth = 12
        val taskWidth = (width - idWidth - statusWidth - providerWidth - updatedWidth - 4).coerceAtLeast(10)

        val out = mutableListOf<String>()
        out += theme.metadata(
            TerminalText.padEnd("ID", idWidth) + " " +
                TerminalText.padEnd("STATUS", statusWidth) + " " +
                TerminalText.padEnd("TASK", taskWidth) + " " +
                TerminalText.padEnd("PROVIDER", providerWidth) + " " +
                TerminalText.padEnd("UPDATED", updatedWidth)
        )
        jobs.forEach { job ->
            out += TerminalText.padEnd(TerminalText.ellipsize(job.id, idWidth - 1), idWidth) + " " +
                TerminalText.padEnd(statusBadge(job.status), statusWidth) + " " +
                TerminalText.padEnd(TerminalText.ellipsize(job.task, taskWidth - 1), taskWidth) + " " +
                TerminalText.padEnd(TerminalText.ellipsize(job.provider ?: "--", providerWidth - 1), providerWidth) + " " +
                TerminalText.ellipsize(job.updatedAt ?: "--", updatedWidth)
        }
        return out
    }

    private fun compactTable(jobs: List<AgentJobSummary>, width: Int): List<String> {
        val idWidth = 12
        val statusWidth = 11
        val taskWidth = (width - idWidth - statusWidth - 2).coerceAtLeast(10)

        val out = mutableListOf<String>()
        out += theme.metadata(
            TerminalText.padEnd("ID", idWidth) + " " +
                TerminalText.padEnd("STATUS", statusWidth) + " " +
                TerminalText.padEnd("TASK", taskWidth)
        )
        jobs.forEach { job ->
            out += TerminalText.padEnd(TerminalText.ellipsize(job.id, idWidth - 1), idWidth) + " " +
                TerminalText.padEnd(statusBadge(job.status), statusWidth) + " " +
                TerminalText.ellipsize(job.task, taskWidth)
        }
        return out
    }

    private fun stackedList(jobs: List<AgentJobSummary>, width: Int): List<String> {
        val out = mutableListOf<String>()
        jobs.forEach { job ->
            out += TerminalText.ellipsize(statusBadge(job.status) + " " + job.id, width)
            out += TerminalText.ellipsize(theme.subdued("  ") + job.task, width)
            job.note?.takeIf { it.isNotBlank() }?.let {
                out += TerminalText.ellipsize(theme.subdued("  ") + it, width)
            }
        }
        return out
    }
}
