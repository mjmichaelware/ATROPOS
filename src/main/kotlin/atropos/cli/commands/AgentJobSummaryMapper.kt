package atropos.cli.commands

import atropos.cli.ui.AgentJobEvent
import atropos.cli.ui.AgentJobStatus as UiAgentJobStatus
import atropos.cli.ui.AgentJobSummary
import atropos.core.agent.AgentJobRecord
import atropos.core.agent.AgentPatchExtractor
import java.nio.file.Files
import java.nio.file.Path
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class AgentJobSummaryMapper(
    private val patchDirectory: Path,
    private val patchExtractor: AgentPatchExtractor
) {
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault())

    fun toJobSummary(job: AgentJobRecord): AgentJobSummary =
        AgentJobSummary(
            id = job.id,
            task = job.task,
            status = toUiStatus(job),
            provider = job.provider.takeIf { it.isNotBlank() },
            patchId = displayPatchId(job),
            verificationId = job.verificationId?.takeIf { it.isNotBlank() },
            smokeCommand = job.smokeCommand?.takeIf { it.isNotBlank() },
            smokeSummary = smokeSummary(job),
            finalReport = job.finalReport?.takeIf { it.isNotBlank() },
            commitProposal = job.commitProposal?.takeIf { it.isNotBlank() },
            nextSuggestedCommand = job.nextSuggestedCommand?.takeIf { it.isNotBlank() },
            contextExportPath = job.contextExportPath?.takeIf { it.isNotBlank() },
            startedAt = formatInstant(job.startedAt),
            updatedAt = formatInstant(job.updatedAt),
            changedPathsCount = changedPathsCount(job),
            note = note(job)
        )

    fun timelineEntries(job: AgentJobRecord): List<AgentJobEvent> = buildList {
        addEvent(job.planAt, UiAgentJobStatus.PLANNING, null)
        addEvent(job.patchAt, UiAgentJobStatus.PATCHING, null)
        addEvent(job.applyAt, UiAgentJobStatus.APPLYING, applyNote(job))
        addEvent(job.verificationAt, UiAgentJobStatus.VERIFYING, verificationNote(job))
        addEvent(job.repairAt, UiAgentJobStatus.REPAIRING, repairNote(job))
        job.finishedAt?.let { finished ->
            add(
                AgentJobEvent(
                    at = formatInstant(finished),
                    status = toUiStatus(job),
                    note = terminalNote(job)
                )
            )
        }
    }.distinctBy { it.at to it.status to it.note }

    private fun MutableList<AgentJobEvent>.addEvent(
        instant: java.time.Instant?,
        status: UiAgentJobStatus,
        note: String?
    ) {
        if (instant != null) add(AgentJobEvent(at = formatInstant(instant), status = status, note = note))
    }

    private fun toUiStatus(job: AgentJobRecord): UiAgentJobStatus = when (job.status) {
        atropos.core.agent.AgentJobStatus.PLANNING -> UiAgentJobStatus.PLANNING
        atropos.core.agent.AgentJobStatus.PATCHING -> UiAgentJobStatus.PATCHING
        atropos.core.agent.AgentJobStatus.APPLYING -> UiAgentJobStatus.APPLYING
        atropos.core.agent.AgentJobStatus.REPAIRING -> UiAgentJobStatus.REPAIRING
        atropos.core.agent.AgentJobStatus.COMPLETED -> UiAgentJobStatus.PASSED
        atropos.core.agent.AgentJobStatus.FAILED -> if (looksRefused(job)) UiAgentJobStatus.REFUSED else UiAgentJobStatus.FAILED
        atropos.core.agent.AgentJobStatus.REFUSED -> UiAgentJobStatus.REFUSED
    }

    private fun looksRefused(job: AgentJobRecord): Boolean {
        val text = listOfNotNull(job.failureReason, job.result, job.patchResult, job.applyResult, job.repairResult, job.smokeResult, job.finalReport)
            .joinToString(" ")
            .lowercase()
        return text.contains("refus") ||
            text.contains("unsafe") ||
            text.contains("forbidden") ||
            text.contains("no unified diff") ||
            text.contains("bad diff") ||
            text.contains("invalid patch")
    }

    private fun displayPatchId(job: AgentJobRecord): String? =
        job.appliedPatchId?.takeIf { it.isNotBlank() }
            ?: job.patchId?.takeIf { it.isNotBlank() }

    private fun changedPathsCount(job: AgentJobRecord): Int? {
        val patchId = displayPatchId(job) ?: return null
        val diffFile = patchDirectory.resolve("$patchId.diff").normalize()
        if (!diffFile.startsWith(patchDirectory) || !Files.isRegularFile(diffFile)) return null
        val diffText = runCatching { Files.readString(diffFile) }.getOrNull() ?: return null
        return patchExtractor.extract(diffText)?.touchedPaths?.size
    }

    private fun note(job: AgentJobRecord): String? =
        when (job.status) {
            atropos.core.agent.AgentJobStatus.FAILED,
            atropos.core.agent.AgentJobStatus.REFUSED -> job.failureReason?.takeIf { it.isNotBlank() }
                ?: job.finalReport?.takeIf { it.isNotBlank() }
                ?: smokeSummary(job)
                ?: job.result
            else -> job.finalReport?.takeIf { it.isNotBlank() }
                ?: smokeSummary(job)
                ?: job.result
        }?.takeIf { it.isNotBlank() }

    private fun terminalNote(job: AgentJobRecord): String? =
        when (toUiStatus(job)) {
            UiAgentJobStatus.PASSED -> job.finalReport?.takeIf { it.isNotBlank() } ?: job.result?.takeIf { it.isNotBlank() }
            UiAgentJobStatus.FAILED, UiAgentJobStatus.REFUSED -> job.failureReason?.takeIf { it.isNotBlank() }
                ?: job.finalReport?.takeIf { it.isNotBlank() }
                ?: job.result
            else -> null
        }?.takeIf { it.isNotBlank() }

    private fun applyNote(job: AgentJobRecord): String? =
        job.applyResult?.lineSequence()?.firstOrNull { it.isNotBlank() }?.trim()?.takeIf { it.isNotBlank() }

    private fun verificationNote(job: AgentJobRecord): String? =
        job.verificationId?.takeIf { it.isNotBlank() }?.let { "verification $it" }

    private fun repairNote(job: AgentJobRecord): String? =
        job.repairId?.takeIf { it.isNotBlank() }?.let { "repair $it" }

    private fun smokeSummary(job: AgentJobRecord): String? {
        job.smokeResult?.takeIf { it.isNotBlank() }?.let { return it }
        job.smokeCommand?.takeIf { it.isNotBlank() }?.let { command ->
            val resultText = when {
                job.smokePassed == true -> "passed"
                job.smokePassed == false && job.smokeExitCode != null -> "failed exit ${job.smokeExitCode}"
                job.smokePassed == false -> "failed"
                else -> "not run"
            }
            val durationText = job.smokeDurationMillis?.let { "${it} ms" } ?: "unknown duration"
            return "$resultText · $command · $durationText"
        }
        return null
    }

    private fun formatInstant(instant: java.time.Instant?): String =
        instant?.let { timeFormatter.format(it) } ?: "unknown"
}
