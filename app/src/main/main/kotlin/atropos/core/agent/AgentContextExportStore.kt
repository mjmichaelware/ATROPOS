package atropos.core.agent

import atropos.core.AtroposRepoRootLocator
import atropos.core.security.RedactionFilter
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

class AgentContextExportStore(
    private val repoRoot: Path = AtroposRepoRootLocator.resolve(),
    private val redactionFilter: RedactionFilter = RedactionFilter()
) {
    private val exportDir = repoRoot.resolve(".atropos/agent/context").normalize()

    fun exportDirectory(): Path = exportDir

    fun write(record: AgentJobRecord, changedFiles: List<String>): Path {
        Files.createDirectories(exportDir)
        val jobFile = exportDir.resolve("${record.id}.txt")
        val latestFile = exportDir.resolve("latest.txt")
        val content = render(record, changedFiles)

        Files.writeString(jobFile, content, StandardCharsets.UTF_8)
        Files.writeString(latestFile, content, StandardCharsets.UTF_8)
        return jobFile
    }

    private fun render(record: AgentJobRecord, changedFiles: List<String>): String = buildString {
        appendLine("latest job id: ${record.id}")
        appendLine("task: ${redactionFilter.redact(record.task)}")
        appendLine("source: ${record.sourceEvidence?.let(redactionFilter::redact) ?: "unresolved"}")
        appendLine("impacted symbols: ${record.impactedSymbols.joinToString(", ") { redactionFilter.redact(it) }.ifBlank { "none" }}")
        appendLine("changed files: ${changedFiles.joinToString(", ") { redactionFilter.redact(it) }.ifBlank { "none" }}")
        appendLine("verification id: ${record.verificationId ?: "none"}")
        appendLine("smoke result: ${record.smokeResult?.let(redactionFilter::redact) ?: "none"}")
        appendLine("final status: ${renderStatus(record.status)}")
        appendLine("next recommended pass/action: ${record.nextSuggestedCommand?.let(redactionFilter::redact) ?: "none"}")
        appendLine("commit proposal:")
        appendLine(record.commitProposal?.let(redactionFilter::redact) ?: "none")
        appendLine("final report:")
        appendLine(record.finalReport?.let(redactionFilter::redact) ?: "none")
        appendLine("context export path: ${exportDir.resolve("${record.id}.txt")}")
    }.trimEnd() + "\n"

    private fun renderStatus(status: AgentJobStatus): String = when (status) {
        AgentJobStatus.COMPLETED -> "passed"
        AgentJobStatus.FAILED -> "failed"
        AgentJobStatus.REFUSED -> "refused"
        AgentJobStatus.PLANNING,
        AgentJobStatus.PATCHING,
        AgentJobStatus.APPLYING,
        AgentJobStatus.REPAIRING -> status.name.lowercase()
    }
}
