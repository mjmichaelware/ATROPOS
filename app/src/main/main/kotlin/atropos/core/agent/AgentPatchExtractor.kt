package atropos.core.agent

import java.nio.file.Path

data class AgentPatchExtraction(
    val diff: String,
    val touchedPaths: List<String>,
    val hasHunkBody: Boolean
)

class AgentPatchExtractor {
    fun extract(raw: String): AgentPatchExtraction? {
        val diff = extractUnifiedDiff(raw) ?: return null
        val touchedPaths = touchedPaths(diff)
        return AgentPatchExtraction(
            diff = diff.trimEnd(),
            touchedPaths = touchedPaths,
            hasHunkBody = containsHunkBody(diff)
        )
    }

    fun validate(diff: String): String? {
        val banned = listOf(
            Regex("""(^|/)\.env($|\.)"""),
            Regex("""(^|/)\.atropos/secrets(/|$)"""),
            Regex("""(^|/)token\.json$"""),
            Regex("""(^|/)credentials\.json$"""),
            Regex("""(^|/)client_secret\.json$"""),
            Regex("""(^|/)build(/|$)"""),
            Regex("""(^|/)\.gradle(/|$)"""),
            Regex("""(^|/)\.git(/|$)"""),
            Regex(""".*\.jar$"""),
            Regex(""".*\.class$""")
        )

        touchedPaths(diff).forEach { rawPath ->
            val normalized = runCatching { normalizePath(rawPath) }.getOrNull()
                ?: return "patch touches invalid path: $rawPath"
            if (normalized.isBlank() || normalized.startsWith("../") || normalized.startsWith("/")) {
                return "patch touches invalid path: $rawPath"
            }
            if (banned.any { it.containsMatchIn(normalized) }) {
                return "patch touches forbidden path: $rawPath"
            }
        }

        return null
    }

    fun preview(raw: String, limit: Int = 300): String {
        val collapsed = raw.replace(Regex("\\s+"), " ").trim()
        if (collapsed.length <= limit) return collapsed
        return collapsed.take(limit - 3) + "..."
    }

    private fun extractUnifiedDiff(raw: String): String? {
        val lines = raw.lineSequence().toList()
        val start = lines.indexOfFirst {
            val trimmed = it.trimStart()
            trimmed.startsWith("diff --git ") ||
                trimmed.startsWith("--- ") ||
                trimmed.startsWith("--- a/") ||
                trimmed.startsWith("--- src/")
        }

        if (start < 0) return null

        val cleaned = buildString {
            var started = false
            var inHunk = false

            for (line in lines.drop(start)) {
                val trimmed = line.trim()
                val leading = line.trimStart()

                if (trimmed == "```") break
                if (leading.startsWith("```")) continue

                val allowedHeader =
                    leading.startsWith("diff --git ") ||
                        leading.startsWith("index ") ||
                        leading.startsWith("new file mode ") ||
                        leading.startsWith("deleted file mode ") ||
                        leading.startsWith("old mode ") ||
                        leading.startsWith("new mode ") ||
                        leading.startsWith("similarity index ") ||
                        leading.startsWith("rename from ") ||
                        leading.startsWith("rename to ") ||
                        leading.startsWith("copy from ") ||
                        leading.startsWith("copy to ") ||
                        leading.startsWith("--- ") ||
                        leading.startsWith("+++ ")

                val hunkLine =
                    leading.startsWith("@@") ||
                        leading.startsWith(" ") ||
                        leading.startsWith("+") ||
                        leading.startsWith("-") ||
                        leading.startsWith("\\ No newline at end of file")

                when {
                    !started && allowedHeader -> {
                        started = true
                        appendLine(line)
                        if (leading.startsWith("@@")) inHunk = true
                    }
                    started && allowedHeader -> {
                        appendLine(line)
                        if (leading.startsWith("@@")) inHunk = true
                    }
                    started && hunkLine -> {
                        appendLine(line)
                        if (leading.startsWith("@@")) inHunk = true
                    }
                    started && inHunk && trimmed.isBlank() -> appendLine(line)
                    started -> break
                }
            }
        }.trim()

        return cleaned.takeIf {
            (it.startsWith("diff --git ") || it.startsWith("--- ")) &&
                (it.startsWith("@@") || it.contains("\n@@"))
        }
    }

    private fun isDiffHeader(line: String): Boolean =
        line.startsWith("```diff") || line.startsWith("```patch")

    private fun touchedPaths(diff: String): List<String> {
        val paths = linkedSetOf<String>()
        for (line in diff.lineSequence()) {
            when {
                line.startsWith("diff --git ") -> {
                    val parts = line.removePrefix("diff --git ").split(" ")
                    parts.getOrNull(0)?.let { paths += stripPrefix(it) }
                    parts.getOrNull(1)?.let { paths += stripPrefix(it) }
                }
                line.startsWith("--- ") || line.startsWith("+++ ") -> {
                    line.substring(4).trim().let { paths += stripPrefix(it) }
                }
                line.startsWith("rename from ") || line.startsWith("rename to ") ->
                    paths += line.substringAfter(" ").trim()
                line.startsWith("copy from ") || line.startsWith("copy to ") ->
                    paths += line.substringAfter(" ").trim()
            }
        }
        return paths.filter { it.isNotBlank() }
    }

    private fun containsHunkBody(diff: String): Boolean =
        diff.lineSequence().any { line ->
            when {
                line.startsWith("+++ ") || line.startsWith("--- ") || line.startsWith("diff --git ") -> false
                line.startsWith("@@") -> false
                line.startsWith("\\ No newline at end of file") -> false
                line.startsWith("+") && !line.startsWith("+++") -> true
                line.startsWith("-") && !line.startsWith("---") -> true
                line.startsWith(" ") -> true
                else -> false
            }
        }

    private fun stripPrefix(path: String): String =
        path.removePrefix("a/").removePrefix("b/")
            .trim()
            .trim('"')
            .takeUnless { it == "/dev/null" }
            ?: ""

    private fun normalizePath(path: String): String =
        Path.of(path).normalize().toString().replace('\\', '/')
}
