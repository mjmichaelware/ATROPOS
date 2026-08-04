package atropos.core.agent

import atropos.core.policy.BoundedProcessRunner
import java.nio.file.Path

data class AgentRepoStatusResult(
    val ok: Boolean,
    val files: Set<String> = emptySet(),
    val failure: AgentExecutionFailure? = null,
    val message: String? = null
)

class AgentRunRepoStatus(
    private val repoRoot: Path,
    private val processRunner: BoundedProcessRunner = BoundedProcessRunner()
) {
    /** One porcelain row: the two-character status code and the path it names. */
    data class RepoStatusLine(val code: String, val path: String) {
        fun render(): String = "$code $path"
    }

    fun changedFilesSince(baseline: Set<String>): List<String> {
        val current = captureResult()
        if (!current.ok) return emptyList()
        return (current.files - baseline)
            .filter { isStageableChange(it) }
            .sorted()
    }

    fun capture(): Set<String> = captureResult().files

    /**
     * The porcelain rows with their status codes preserved.
     *
     * [capture] discards the codes because it only answers "which paths moved".
     * A mutation proof has to show the operator the same `git status` evidence a
     * human would read, so the code has to survive. This shares [captureResult]'s
     * bounded runner rather than spawning its own process: an unbounded
     * `ProcessBuilder` here would reopen the timeout and output-truncation hole
     * the bounded runner exists to close.
     */
    fun statusLines(): List<RepoStatusLine> {
        val result = runStatus()
        if (result.launchError != null || result.timedOut || result.exitCode != 0) return emptyList()
        return (result.stdout + result.stderr).trimEnd().lineSequence()
            .mapNotNull { line ->
                val path = parsePorcelainPath(line) ?: return@mapNotNull null
                RepoStatusLine(code = line.take(2).trim(), path = path)
            }
            .toList()
    }

    fun captureResult(): AgentRepoStatusResult {
        val result = runStatus()
        val output = (result.stdout + result.stderr).trimEnd()
        if (result.launchError != null || result.timedOut || result.exitCode != 0) {
            return AgentRepoStatusResult(
                ok = false,
                failure = if (result.launchError != null) AgentExecutionFailure.LAUNCH_FAILED
                else if (result.timedOut) AgentExecutionFailure.TIMEOUT
                else AgentExecutionFailure.REPOSITORY_COMMAND_FAILED,
                message = "git status failed: ${(result.launchError ?: output).take(MAX_MESSAGE_CHARS)}"
            )
        }
        return AgentRepoStatusResult(ok = true, files = output.lineSequence()
            .mapNotNull { parsePorcelainPath(it) }
            .toSet())
    }

    private fun runStatus() = processRunner.run(
        command = listOf("git", "status", "--porcelain", "--untracked-files=all"),
        directory = repoRoot,
        timeoutMillis = STATUS_TIMEOUT_MILLIS,
        maxOutputBytes = MAX_OUTPUT_CHARS,
        maxOutputLines = MAX_OUTPUT_LINES,
        removeEnvironmentKeys = sensitiveEnvironmentKeys()
    )

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

    private companion object {
        const val MAX_OUTPUT_CHARS = 64 * 1024
        const val MAX_OUTPUT_LINES = 2_000
        const val MAX_MESSAGE_CHARS = 240
        const val STATUS_TIMEOUT_MILLIS = 60_000L
    }

    private fun sensitiveEnvironmentKeys(): Set<String> = System.getenv().keys.filter { key ->
        val name = key.uppercase()
        name.contains("TOKEN") || name.contains("SECRET") || name.contains("PASSWORD") ||
            name.endsWith("_KEY") || name.contains("CREDENTIAL")
    }.toSet()
}
