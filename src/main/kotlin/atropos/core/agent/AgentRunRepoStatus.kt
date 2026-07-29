package atropos.core.agent

import java.nio.file.Path

class AgentRunRepoStatus(
    private val repoRoot: Path
) {
    /** One porcelain row: the two-character status code and the path it names. */
    data class RepoStatusLine(val code: String, val path: String) {
        fun render(): String = "$code $path"
    }

    fun changedFilesSince(baseline: Set<String>): List<String> {
        val current = capture()
        return (current - baseline)
            .filter { isStageableChange(it) }
            .sorted()
    }

    fun capture(): Set<String> = statusLines().map { it.path }.toSet()

    /**
     * The porcelain rows with their status codes preserved.
     *
     * [capture] discards the codes because it only answers "which paths moved".
     * A mutation proof has to show the operator the same `git status` evidence a
     * human would read, so the code has to survive.
     */
    fun statusLines(): List<RepoStatusLine> {
        val process = ProcessBuilder("git", "status", "--porcelain", "--untracked-files=all")
            .directory(repoRoot.toFile())
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().readText().trimEnd()
        process.waitFor()
        return output.lineSequence()
            .mapNotNull { line ->
                val path = parsePorcelainPath(line) ?: return@mapNotNull null
                RepoStatusLine(code = line.take(2).trim(), path = path)
            }
            .toList()
    }

    private fun parsePorcelainPath(line: String): String? {
        if (line.length < 4) return null
        val path = line.substring(3).trim()
        if (path.isBlank()) return null
        return path.substringAfter(" -> ", path)
    }

    private fun isStageableChange(path: String): Boolean {
        val normalized = path.replace('\\', '/')
        val name = normalized.substringAfterLast('/')
        if (normalized.startsWith(".atropos/") || normalized == ".atropos") return false
        if (normalized.startsWith(".gradle/") || normalized == ".gradle") return false
        if (normalized.startsWith("build/") || normalized == "build") return false
        if (name.endsWith(".jar") || name.endsWith(".class")) return false
        if (name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".gif") || name.endsWith(".zip")) return false
        if (normalized == ".env" || normalized.startsWith(".env.")) return false
        if (name.contains("token", ignoreCase = true)) return false
        if (name.contains("secret", ignoreCase = true)) return false
        if (name.contains("credential", ignoreCase = true)) return false
        return true
    }
}
