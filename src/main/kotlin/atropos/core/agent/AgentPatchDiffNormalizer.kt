package atropos.core.agent

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

class AgentPatchDiffNormalizer(
    private val repoRoot: Path,
    private val extractor: AgentPatchExtractor = AgentPatchExtractor()
) {
    fun normalize(diffText: String): String {
        val extraction = extractor.extract(diffText) ?: return diffText.trimEnd() + "\n"
        val diff = extraction.diff
        if (!isContextlessAddOnlyPatch(diff, extraction.touchedPaths)) {
            return diff.trimEnd() + "\n"
        }

        val path = extraction.touchedPaths.singleOrNull()?.let(::normalizeRelativePath) ?: return diff.trimEnd() + "\n"
        val target = repoRoot.resolve(path).normalize()
        if (!target.startsWith(repoRoot) || !Files.isRegularFile(target)) {
            return diff.trimEnd() + "\n"
        }

        val addedLines = diff.lineSequence()
            .filter { line -> line.startsWith("+") && !line.startsWith("+++") }
            .map { line -> line.removePrefix("+") }
            .toList()
        if (addedLines.isEmpty()) {
            return diff.trimEnd() + "\n"
        }

        val originalLines = runCatching { Files.readAllLines(target, StandardCharsets.UTF_8) }
            .getOrElse { return diff.trimEnd() + "\n" }

        return buildAppendPatch(path, originalLines, addedLines)
    }

    private fun isContextlessAddOnlyPatch(diff: String, touchedPaths: List<String>): Boolean {
        if (touchedPaths.size != 1) return false

        var sawHunk = false
        for (line in diff.lineSequence()) {
            when {
                line.startsWith("@@") -> sawHunk = true
                line.startsWith("+") && !line.startsWith("+++") -> continue
                line.startsWith("--- ") || line.startsWith("+++ ") || line.startsWith("diff --git ") -> continue
                line.startsWith("\\ No newline at end of file") -> continue
                line.isBlank() -> continue
                sawHunk -> return false
                else -> return false
            }
        }

        return sawHunk
    }

    private fun normalizeRelativePath(path: String): String =
        path.removePrefix("a/").removePrefix("b/").trim().trim('"').trim('\'')

    private fun buildAppendPatch(
        relativePath: String,
        originalLines: List<String>,
        addedLines: List<String>
    ): String {
        val contextSize = minOf(3, originalLines.size)
        val contextLines = originalLines.takeLast(contextSize)
        val originalStartLine = if (originalLines.isEmpty()) 0 else originalLines.size - contextSize + 1
        val originalCount = contextLines.size
        val newCount = originalCount + addedLines.size

        return buildString {
            appendLine("--- a/$relativePath")
            appendLine("+++ b/$relativePath")
            appendLine("@@ -$originalStartLine,$originalCount +$originalStartLine,$newCount @@")
            contextLines.forEach { appendLine(" $it") }
            addedLines.forEach { appendLine("+$it") }
        }
    }
}
