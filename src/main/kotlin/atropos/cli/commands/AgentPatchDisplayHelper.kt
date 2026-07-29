package atropos.cli.commands

import atropos.core.agent.AgentPatchExtractor
import atropos.core.agent.AgentPatchRunResult
import java.nio.file.Files
import java.nio.file.Path

class AgentPatchDisplayHelper(
    private val patchExtractor: AgentPatchExtractor
) {
    fun changedPathsPreview(patchPath: Path?, limit: Int = 6): String? {
        if (patchPath == null || !Files.isRegularFile(patchPath)) return null
        val diffText = runCatching { Files.readString(patchPath) }.getOrNull() ?: return null
        val paths = patchExtractor.extract(diffText)?.touchedPaths ?: return null
        if (paths.isEmpty()) return null
        val shown = paths.take(limit).joinToString(", ")
        val remaining = paths.size - limit
        return if (remaining > 0) "$shown (+$remaining more)" else shown
    }

    fun nextPatchCommand(result: AgentPatchRunResult): String = when {
        result.patchId == null -> "/agent patch <task>"
        result.checkResult == null -> "/agent apply --check ${result.patchId}"
        result.checkResult.passed -> "/agent apply --check ${result.patchId}  (check already OK)"
        else -> "/agent patch <task>  (git apply --check failed, regenerate)"
    }
}
