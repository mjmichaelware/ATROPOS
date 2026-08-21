package atropos.cli.commands

import atropos.core.agent.AgentPatchExtractor
import atropos.core.agent.AgentPatchRunResult
import atropos.cli.ui.DiffContentParser
import java.nio.file.Files
import java.nio.file.Path

class AgentPatchDisplayHelper(
    private val patchExtractor: AgentPatchExtractor
) {
    private val diffParser = DiffContentParser()

    fun changedPathsPreview(patchPath: Path?, limit: Int = 6): String? {
        if (patchPath == null || !Files.isRegularFile(patchPath)) return null
        val diffText = runCatching { Files.readString(patchPath) }.getOrNull() ?: return null
        val paths = patchExtractor.extract(diffText)?.touchedPaths ?: return null
        if (paths.isEmpty()) return null
        val shown = paths.take(limit).joinToString(", ")
        val remaining = paths.size - limit
        return if (remaining > 0) "$shown (+$remaining more)" else shown
    }

    /**
     * Returns a rich summary of the diff content: file count, total additions,
     * total deletions, and per-file change summaries.
     */
    fun richDiffSummary(patchPath: Path?, limit: Int = 8): String? {
        if (patchPath == null || !Files.isRegularFile(patchPath)) return null
        val diffText = runCatching { Files.readString(patchPath) }.getOrNull() ?: return null
        if (diffText.isBlank()) return null

        val diff = diffParser.parse(diffText)
        if (diff.files.isEmpty()) return null

        return buildString {
            appendLine("${diff.totalFiles} file${if (diff.totalFiles != 1) "s" else ""} · +${diff.totalAdditions} -${diff.totalDeletions}")
            diff.files.take(limit).forEach { file ->
                val status = when {
                    file.isNewFile -> "new"
                    file.isDeletedFile -> "del"
                    file.isRename -> "ren"
                    else -> "mod"
                }
                appendLine("  $status ${file.displayPath} (+${file.totalAdditions}/-${file.totalDeletions})")
            }
            val hidden = diff.files.size - limit
            if (hidden > 0) append("  +$hidden more files")
        }.trimEnd()
    }

    fun nextPatchCommand(result: AgentPatchRunResult): String = when {
        result.patchId == null -> "/agent patch <task>"
        result.checkResult == null -> "/agent apply --check ${result.patchId}"
        result.checkResult.passed -> "/agent apply --check ${result.patchId}  (check already OK)"
        else -> "/agent patch <task>  (git apply --check failed, regenerate)"
    }
}
