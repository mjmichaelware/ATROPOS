package atropos.core.agent

import atropos.core.security.RedactionFilter

class AgentRunReporter(
    private val redactionFilter: RedactionFilter = RedactionFilter()
) {
    fun buildFinalReport(
        job: AgentJobRecord,
        task: String,
        smokeCommand: String?,
        smokeExecution: AgentSmokeExecutionResult?,
        changedFiles: List<String>,
        sourceEvidence: SourceEvidence,
        impactedSymbols: List<String>
    ): String = buildString {
        appendLine("status: ${renderFinalStatus(job.status)}")
        appendLine("task: ${compactTask(task)}")
        appendLine("provider: ${job.provider}")
        appendLine("patch: ${job.appliedPatchId ?: job.patchId ?: "none"}")
        appendLine("verification: ${job.verificationId ?: "none"}")
        appendLine("smoke: ${smokeExecution?.summary()?.let(redactionFilter::redact) ?: smokeCommand?.let { "not run" } ?: "not requested"}")
        appendLine("source: ${sourceEvidence.describe(redactionFilter::redact)}")
        appendLine("impacted symbols: ${impactedSymbols.joinToString(", ") { redactionFilter.redact(it) }.ifBlank { "none" }}")
        appendLine("changed files: ${changedFiles.joinToString(", ") { redactionFilter.redact(it) }.ifBlank { "none" }}")
    }.trimEnd()

    fun buildCommitProposal(
        task: String,
        smokeCommand: String?,
        changedFiles: List<String>,
        smokeExecution: AgentSmokeExecutionResult?
    ): String = buildString {
        appendLine("files to stage:")
        if (changedFiles.isEmpty()) {
            appendLine("  none")
        } else {
            changedFiles.forEach { path -> appendLine("  - ${redactionFilter.redact(path)}") }
        }
        appendLine("suggested commit message:")
        appendLine("  ${buildCommitMessage(task, smokeCommand, smokeExecution)}")
    }.trimEnd()

    fun buildNextSuggestedCommand(
        task: String,
        smokeCommand: String?,
        changedFiles: List<String>,
        job: AgentJobRecord,
        smokeExecution: AgentSmokeExecutionResult?
    ): String {
        return when {
            smokeExecution != null && !smokeExecution.passed ->
                smokeCommand?.takeIf { it.isNotBlank() }?.let {
                    "review smoke failure, then rerun /agent run --smoke \"${escapeQuotes(redactionFilter.redact(it))}\" ${compactTask(task, 48)}"
                } ?: "review smoke failure, then rerun /agent run"
            job.status == AgentJobStatus.COMPLETED && changedFiles.isNotEmpty() -> {
                val commitMessage = buildCommitMessage(task, smokeCommand, smokeExecution)
                "git add ${changedFiles.joinToString(" ") { redactionFilter.redact(it) }} && git commit -m \"${escapeQuotes(commitMessage)}\""
            }
            job.status == AgentJobStatus.COMPLETED -> "git status --short"
            job.status == AgentJobStatus.FAILED -> "/agent repair ${job.patchId ?: "latest"}"
            else -> "/agent job ${job.id}"
        }
    }

    fun buildSafeSmokeCommandSuggestion(task: String): String =
        "choose a safe smoke command, then rerun /agent run --smoke \"<safe smoke command>\" ${compactTask(task, 48)}"

    fun compactTask(task: String, maxChars: Int = 80): String {
        val collapsed = redactionFilter.redact(task).replace(Regex("\\s+"), " ").trim()
        if (collapsed.length <= maxChars) return collapsed
        return collapsed.take(maxChars - 3) + "..."
    }

    private fun buildCommitMessage(
        task: String,
        smokeCommand: String?,
        smokeExecution: AgentSmokeExecutionResult?
    ): String {
        val core = compactTask(task, 60)
        val smokeSuffix = when {
            smokeExecution?.passed == true -> " smoke"
            smokeExecution != null -> " smoke-failed"
            smokeCommand != null -> " smoke-pending"
            else -> ""
        }
        return "ATROPOS pass 11: $core$smokeSuffix".trim()
    }

    private fun escapeQuotes(text: String): String = text.replace("\"", "\\\"")

    private fun renderFinalStatus(status: AgentJobStatus): String = when (status) {
        AgentJobStatus.COMPLETED -> "passed"
        AgentJobStatus.FAILED -> "failed"
        AgentJobStatus.REFUSED -> "refused"
        AgentJobStatus.PLANNING,
        AgentJobStatus.PATCHING,
        AgentJobStatus.APPLYING,
        AgentJobStatus.REPAIRING -> status.name.lowercase()
    }
}
