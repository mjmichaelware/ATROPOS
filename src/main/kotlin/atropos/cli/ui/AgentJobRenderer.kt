/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.ui

/**
 * UI-only status vocabulary for Pass 10 agent jobs. This enum exists so the renderer has a
 * fixed, known set of labels to color and align — it does not model backend job execution.
 */
enum class AgentJobStatus(val label: String) {
    QUEUED("queued"),
    PLANNING("planning"),
    PATCHING("patching"),
    APPLYING("applying"),
    VERIFYING("verifying"),
    REPAIRING("repairing"),
    PASSED("passed"),
    FAILED("failed"),
    REFUSED("refused");

    companion object {
        fun fromLabel(value: String): AgentJobStatus? =
            entries.firstOrNull { it.label.equals(value.trim(), ignoreCase = true) }
    }
}

/** UI-facing shape of one agent job. Populated by whatever wires the job backend in later; not fetched here. */
data class AgentJobSummary(
    val id: String,
    val task: String,
    val status: AgentJobStatus,
    val provider: String? = null,
    val patchId: String? = null,
    val verificationId: String? = null,
    val smokeCommand: String? = null,
    val smokeSummary: String? = null,
    val finalReport: String? = null,
    val commitProposal: String? = null,
    val nextSuggestedCommand: String? = null,
    val contextExportPath: String? = null,
    val startedAt: String? = null,
    val updatedAt: String? = null,
    val changedPathsCount: Int? = null,
    val note: String? = null
)

/** One timeline entry for the `/agent job <id>` detail view. */
data class AgentJobEvent(
    val at: String,
    val status: AgentJobStatus,
    val note: String? = null
)

class AgentJobRenderer(
    private val theme: TerminalTheme
) {
    fun renderRunSummary(job: AgentJobSummary, width: Int): List<String> {
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

/**
 * Deterministic sample data for manual width checks (40/80/120 cols) and previews.
 * Not referenced by any command path — isolated from runtime wiring on purpose.
 */
object AgentJobRendererPreview {
    fun sampleJobs(): List<AgentJobSummary> = listOf(
        AgentJobSummary(
            id = "job-20260702-0001",
            task = "Add a one-line comment to README noting ATROPOS owns repo edits",
            status = AgentJobStatus.VERIFYING,
            provider = "github_models",
            patchId = "patch-20260702-153012-github_models",
            startedAt = "17:28:11",
            updatedAt = "17:28:47",
            changedPathsCount = 1
        ),
        AgentJobSummary(
            id = "job-20260702-0002",
            task = "Refactor QuotaLedger cooldown window into a named constant",
            status = AgentJobStatus.PASSED,
            provider = "groq",
            patchId = "patch-20260702-140501-groq",
            startedAt = "14:05:01",
            updatedAt = "14:06:22",
            changedPathsCount = 2
        ),
        AgentJobSummary(
            id = "job-20260702-0003",
            task = "Patch attempt for provider fallback ordering in AgentProviderSelector",
            status = AgentJobStatus.REFUSED,
            provider = "sambanova",
            patchId = null,
            startedAt = "12:00:03",
            updatedAt = "12:00:09",
            note = "diff touched a forbidden path"
        ),
        AgentJobSummary(
            id = "job-20260702-0004",
            task = "queued task, not started yet",
            status = AgentJobStatus.QUEUED,
            provider = null,
            updatedAt = "17:30:00"
        )
    )

    fun sampleTimeline(): List<AgentJobEvent> = listOf(
        AgentJobEvent(at = "17:28:11", status = AgentJobStatus.QUEUED),
        AgentJobEvent(at = "17:28:14", status = AgentJobStatus.PLANNING),
        AgentJobEvent(at = "17:28:22", status = AgentJobStatus.PATCHING),
        AgentJobEvent(at = "17:28:35", status = AgentJobStatus.APPLYING, note = "git apply --check ok"),
        AgentJobEvent(at = "17:28:47", status = AgentJobStatus.VERIFYING)
    )

    /** Renders the three views at 40/80/120 columns; call from a scratch main() when eyeballing layout. */
    fun renderAllWidths(renderer: AgentJobRenderer): Map<Int, List<String>> {
        val jobs = sampleJobs()
        return listOf(40, 80, 120).associateWith { width ->
            buildList {
                addAll(renderer.renderRunSummary(jobs[0], width))
                add("")
                addAll(renderer.renderJobsList(jobs, width))
                add("")
                addAll(renderer.renderJobDetail(jobs[0], sampleTimeline(), width))
            }
        }
    }
}
