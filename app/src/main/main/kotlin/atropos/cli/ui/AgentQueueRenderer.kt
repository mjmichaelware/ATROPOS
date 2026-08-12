package atropos.cli.ui

import atropos.core.agent.AgentQueueRecord
import atropos.core.agent.AgentQueueState
import atropos.core.security.RedactionFilter

class AgentQueueRenderer(
    private val theme: TerminalTheme,
    private val redactionFilter: RedactionFilter = RedactionFilter()
) {
    fun renderList(entries: List<AgentQueueRecord>, width: Int): List<String> {
        val safeWidth = width.coerceAtLeast(40)
        val out = mutableListOf<String>()
        out += divider("AGENT QUEUE", safeWidth)
        if (entries.isEmpty()) {
            out += theme.subdued("queue empty")
            return out.map { TerminalText.ellipsize(it, safeWidth) }
        }

        if (safeWidth >= 100) {
            out += theme.metadata("ID".padEnd(22) + "STATE".padEnd(12) + "ATT".padEnd(6) + "JOB".padEnd(18) + "CHECKPOINT".padEnd(18) + "TASK")
            entries.forEach { entry ->
                out += entry.id.padEnd(22) +
                    badge(entry.state).padEnd(12) +
                    "${entry.attempts}/${entry.maxAttempts}".padEnd(6) +
                    (entry.jobId ?: "none").padEnd(18) +
                    entry.checkpoint.name.padEnd(18) +
                    compact(redactionFilter.redact(entry.task), safeWidth - 76)
            }
        } else {
            entries.forEach { entry ->
                val lease = entry.lease?.let { " lease=${if (it.isLive(java.time.Instant.now())) "live" else "expired"}" } ?: ""
                val eligible = entry.nextEligibleAt?.let { " next=$it" } ?: ""
                out += "${badge(entry.state)} ${entry.id} attempts=${entry.attempts}/${entry.maxAttempts}$lease"
                out += theme.subdued("  checkpoint=${entry.checkpoint} job=${entry.jobId ?: "none"}$eligible")
                out += "  ${compact(redactionFilter.redact(entry.task), safeWidth - 2)}"
            }
        }
        return out.map { TerminalText.ellipsize(it, safeWidth) }
    }

    fun renderDetail(entry: AgentQueueRecord, width: Int): List<String> {
        val safeWidth = width.coerceAtLeast(40)
        val out = mutableListOf<String>()
        out += divider("QUEUE ${entry.id}", safeWidth)
        out += row("state", badge(entry.state), safeWidth)
        out += row("task", redactionFilter.redact(entry.task), safeWidth)
        out += row("smoke", entry.smokeCommand?.let(redactionFilter::redact) ?: theme.subdued("none"), safeWidth)
        out += row("checkpoint", entry.checkpoint.name, safeWidth)
        out += row("attempts", "${entry.attempts}/${entry.maxAttempts}", safeWidth)
        out += row("job", entry.jobId ?: theme.subdued("none"), safeWidth)
        out += row("provider", entry.provider ?: theme.subdued("none"), safeWidth)
        out += row("patch", entry.appliedPatchId ?: entry.patchId ?: theme.subdued("none"), safeWidth)
        out += row("verification", entry.verificationId ?: theme.subdued("none"), safeWidth)
        out += row("lease owner", entry.lease?.owner?.let(redactionFilter::redact) ?: theme.subdued("none"), safeWidth)
        out += row("lease exp", entry.lease?.expiresAt?.toString() ?: theme.subdued("none"), safeWidth)
        out += row("cancel", cancellationText(entry), safeWidth)
        entry.failureReason?.takeIf { it.isNotBlank() }?.let { out += row("failure", redactionFilter.redact(it), safeWidth) }
        out += row("next", theme.code(entry.nextCommand()), safeWidth)
        return out.map { TerminalText.ellipsize(it, safeWidth) }
    }

    private fun cancellationText(entry: AgentQueueRecord): String =
        if (entry.cancellationRequested) {
            "requested${entry.cancellationReason?.let { ": ${redactionFilter.redact(it)}" } ?: ""}"
        } else {
            "none"
        }

    private fun badge(state: AgentQueueState): String {
        val text = "[${state.name.lowercase()}]"
        return when (state) {
            AgentQueueState.COMPLETED -> theme.success(text)
            AgentQueueState.FAILED,
            AgentQueueState.REFUSED,
            AgentQueueState.CANCELLED,
            AgentQueueState.CORRUPT -> theme.error(text)
            AgentQueueState.QUEUED,
            AgentQueueState.RETRY_WAIT -> theme.warning(text)
            AgentQueueState.LEASED,
            AgentQueueState.RUNNING -> theme.brand(text)
        }
    }

    private fun divider(title: String, width: Int): String =
        TerminalText.ellipsize(theme.brand("-- $title --"), width)

    private fun row(label: String, value: String, width: Int): String {
        val prefix = theme.metadata(label.padEnd(13)) + " "
        return TerminalText.ellipsize(prefix + value, width)
    }

    private fun compact(text: String, maxChars: Int): String {
        val collapsed = text.replace(Regex("\\s+"), " ").trim()
        if (collapsed.length <= maxChars) return collapsed
        return collapsed.take(maxChars.coerceAtLeast(4) - 3) + "..."
    }
}
