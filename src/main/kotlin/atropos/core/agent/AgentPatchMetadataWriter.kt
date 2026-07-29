package atropos.core.agent

import atropos.core.security.RedactionFilter
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.time.format.DateTimeFormatter

class AgentPatchMetadataWriter(
    private val patchDir: Path,
    private val clock: () -> Instant,
    private val formatter: DateTimeFormatter,
    private val redactionFilter: RedactionFilter = RedactionFilter()
) {
    fun writeMeta(record: AgentPatchRecord, check: AgentPatchCheckResult) {
        val content = buildString {
            appendLine("id=${record.id}")
            appendLine("provider=${record.provider}")
            appendLine("createdAt=${record.createdAt}")
            appendLine("task=${redactionFilter.compact(record.task.replace("\n", " ").trim(), 1_000)}")
            appendLine("contextBytes=${record.contextBytes}")
            appendLine("diffBytes=${record.diffBytes}")
            appendLine("gitApplyCheckStatus=${check.statusText}")
            appendLine("gitApplyCheckExitCode=${check.exitCode}")
            appendLine("gitApplyCheckOutput=${redactionFilter.compact(compactOutput(check.output), 2_000)}")
            appendLine("diffFile=${record.diffFile.fileName}")
        }
        Files.writeString(record.metaFile, content, StandardCharsets.UTF_8)
    }

    fun writeApplyMeta(
        snapshot: AgentPatchSnapshot,
        checkOnly: Boolean,
        checkResult: AgentPatchCheckResult,
        applyResult: AgentPatchCheckResult?,
        refusalReason: String?,
        changedPaths: List<String>
    ): Path {
        Files.createDirectories(patchDir)
        val createdAt = clock()
        val logFile = patchDir.resolve("apply-${formatter.format(createdAt)}-${snapshot.id}.meta")
        val content = buildString {
            appendLine("patchId=${snapshot.id}")
            appendLine("patchFile=${snapshot.patchFile.fileName}")
            appendLine("checkOnly=$checkOnly")
            appendLine("applied=${applyResult?.passed == true && refusalReason == null}")
            appendLine("changedPaths=${changedPaths.joinToString(",")}")
            appendLine("gitApplyCheckStatus=${checkResult.statusText}")
            appendLine("gitApplyCheckExitCode=${checkResult.exitCode}")
            appendLine("gitApplyCheckOutput=${compactOutput(checkResult.output)}")
            appendLine("gitApplyExitCode=${applyResult?.exitCode ?: ""}")
            appendLine("gitApplyOutput=${compactOutput(applyResult?.output.orEmpty())}")
            appendLine("refusalReason=${refusalReason ?: ""}")
        }
        Files.writeString(logFile, content, StandardCharsets.UTF_8)
        return logFile
    }

    fun compactOutput(raw: String, maxLines: Int = 8, maxChars: Int = 1200): String {
        if (raw.isBlank()) return "no output"
        val lines = raw.lineSequence().take(maxLines).joinToString(" | ").trim()
        return if (lines.length <= maxChars) lines else lines.take(maxChars - 3) + "..."
    }
}
