package atropos.core.agent

import atropos.core.policy.BoundedProcessRunner
import atropos.core.security.RedactionFilter
import java.nio.file.Path

class SelfHostGitStatusEvidence(
    private val repoRoot: Path,
    private val redactionFilter: RedactionFilter = RedactionFilter(),
    private val processRunner: BoundedProcessRunner = BoundedProcessRunner()
) {
    fun capture(paths: List<String> = DEFAULT_PATHS): String = runCatching {
        require(paths.isNotEmpty()) { "status paths are required" }
        require(paths.all(::isBoundedPath)) { "status path escapes repository root" }
        // --untracked-files=all is required, not cosmetic. Plain `git status
        // --short` collapses an untracked directory to a single `?? dir/` row, so a
        // newly created file — exactly what a self-host mutation produces — never
        // appears by name. Evidence that says "something under src/ changed" cannot
        // support a claim about which file was written, which is the whole point of
        // this line.
        val command = listOf("git", "status", "--short", "--untracked-files=all", "--") + paths
        val result = processRunner.run(
            command = command,
            directory = repoRoot,
            timeoutMillis = STATUS_TIMEOUT_MILLIS,
            maxOutputBytes = MAX_CHARS,
            maxOutputLines = MAX_LINES,
            removeEnvironmentKeys = sensitiveEnvironmentKeys()
        )
        val output = (result.stdout + result.stderr).trimEnd()
        val exit = result.exitCode
        val normalized = redactionFilter.redact(output)
            .lineSequence()
            .map { it.trimEnd() }
            .filter { it.isNotBlank() }
            .take(MAX_LINES)
            .joinToString(" | ")
            .ifBlank { "clean" }
        val ok = exit == 0 && !result.timedOut && result.launchError == null
        val failure = when {
            ok -> null
            result.launchError != null -> AgentExecutionFailure.LAUNCH_FAILED.name
            result.timedOut -> AgentExecutionFailure.TIMEOUT.name
            else -> AgentExecutionFailure.REPOSITORY_COMMAND_FAILED.name
        }
        "git_status_short ok=$ok exit=${exit ?: "unavailable"} failure=${failure ?: "none"} paths=${safePathSummary(paths)} output=${normalized.take(MAX_CHARS)}"
    }.getOrElse { failure ->
        "git_status_short ok=false exit=unavailable failure=${AgentExecutionFailure.LAUNCH_FAILED.name} paths=${safePathSummary(paths)} output=${redactionFilter.redact(failure.message?.take(MAX_CHARS) ?: failure.javaClass.simpleName)}"
    }

    private companion object {
        val DEFAULT_PATHS = listOf("src/main/kotlin/atropos", "src/test/kotlin/atropos")
        const val MAX_LINES = 40
        const val MAX_CHARS = 2000
        const val STATUS_TIMEOUT_MILLIS = 60_000L
    }

    private fun isBoundedPath(path: String): Boolean =
        path.isNotBlank() &&
            !path.startsWith("/") &&
            path != "." &&
            !path.split('/').any { it == ".." || it.isBlank() }

    private fun safePathSummary(paths: List<String>): String =
        paths.filter(::isBoundedPath).joinToString(",").ifBlank { "[refused]" }

    private fun sensitiveEnvironmentKeys(): Set<String> = System.getenv().keys.filter { key ->
        val name = key.uppercase()
        name.contains("TOKEN") || name.contains("SECRET") || name.contains("PASSWORD") ||
            name.endsWith("_KEY") || name.contains("CREDENTIAL")
    }.toSet()
}
