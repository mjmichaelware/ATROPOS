package atropos.core.agent

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

class AgentContextExportStore(
    private val repoRoot: Path = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize()
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
        appendLine("task: ${record.task}")
        appendLine("changed files: ${changedFiles.joinToString(", ").ifBlank { "none" }}")
        appendLine("verification id: ${record.verificationId ?: "none"}")
        appendLine("smoke result: ${record.smokeResult ?: "none"}")
        appendLine("final status: ${renderStatus(record.status)}")
        appendLine("next recommended pass/action: ${record.nextSuggestedCommand ?: "none"}")
        appendLine("commit proposal:")
        appendLine(record.commitProposal ?: "none")
        appendLine("final report:")
        appendLine(record.finalReport ?: "none")
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
