package atropos.core.agent

import atropos.core.policy.BoundedProcessRunner
import atropos.core.security.RedactionFilter
import java.nio.file.Files
import java.nio.file.Path

/** Shell-free, repository-bounded process boundary for daemon lifecycle tools. */
class AgentDaemonProcessLauncher(
    private val redactionFilter: RedactionFilter = RedactionFilter(),
    private val processRunner: BoundedProcessRunner = BoundedProcessRunner(),
    private val wakeDirectory: Path = Path.of(System.getProperty("user.dir")),
    private val logWriter: AgentDaemonLogWriter = AgentDaemonLogWriter()
) {
    fun launchForeground(repoRoot: Path, jar: Path, logFile: Path): Process {
        val root = repoRoot.toAbsolutePath().normalize().toRealPath()
        val candidate = jar.toAbsolutePath().normalize()
        val log = logFile.toAbsolutePath().normalize()
        require(candidate.startsWith(root) && candidate.toRealPath().startsWith(root)) {
            "daemon jar is outside ATROPOS root"
        }
        require(log.startsWith(root)) { "daemon log is outside ATROPOS root" }
        require(Files.isRegularFile(candidate) && !Files.isSymbolicLink(candidate)) {
            "daemon jar is missing or symlink-backed"
        }
        Files.createDirectories(log.parent)
        require(!Files.isSymbolicLink(log)) { "daemon log is symlink-backed" }
        require(log.parent.toRealPath().startsWith(root)) { "daemon log parent is outside ATROPOS root" }
        val javaBin = Path.of(System.getProperty("java.home"), "bin", "java").toAbsolutePath().normalize()
        require(Files.isRegularFile(javaBin) && !Files.isSymbolicLink(javaBin)) { "java executable is unavailable" }
        val environment = System.getenv().filterKeys { key ->
            key !in SECRET_ENVIRONMENT_KEYS && !SECRET_ENVIRONMENT_PATTERN.containsMatchIn(key)
        }.toMutableMap().apply {
            this["ATROPOS_ROOT"] = root.toString()
            this["JAVA_HOME"] = Path.of(System.getProperty("java.home")).toString()
        }
        val process = ProcessBuilder(javaBin.toString(), "-Xmx256m", "-jar", candidate.toString(), "--agent-daemon-foreground")
            .directory(root.toFile())
            .redirectInput(ProcessBuilder.Redirect.from(Path.of("/dev/null").toFile()))
            .redirectOutput(ProcessBuilder.Redirect.PIPE)
            .redirectErrorStream(true)
            .apply {
                environment().clear()
                environment().putAll(environment)
            }
            .start()
        logWriter.attach(process, log)
        return process
    }

    fun runWakeTool(tool: String): Result<Int> {
        require(tool == "termux-wake-lock" || tool == "termux-wake-unlock") {
            "unsupported daemon lifecycle tool: ${redactionFilter.redact(tool)}"
        }
        val directory = wakeDirectory.toAbsolutePath().normalize()
        return runCatching {
            val result = processRunner.run(
                command = listOf(tool),
                directory = directory,
                timeoutMillis = 15_000L,
                maxOutputBytes = 8 * 1024,
                maxOutputLines = 100
            )
            when {
                result.launchError != null -> Result.failure(IllegalStateException("wake tool launch failed"))
                result.timedOut -> Result.failure(IllegalStateException("wake tool timed out"))
                result.outputTruncated -> Result.failure(IllegalStateException("wake tool output exceeded bounds"))
                result.exitCode != 0 -> Result.failure(IllegalStateException("wake tool exited unsuccessfully"))
                else -> Result.success(0)
            }
        }.getOrElse { Result.failure(IllegalStateException(redactionFilter.redact(it.message ?: "wake tool failed"))) }
    }

    private companion object {
        val SECRET_ENVIRONMENT_PATTERN = Regex("(?i)(KEY|TOKEN|SECRET|PASSWORD|CREDENTIAL)")
        val SECRET_ENVIRONMENT_KEYS = setOf(
            "ATROPOS_API_KEY",
            "OPENAI_API_KEY",
            "GROQ_API_KEY",
            "GEMINI_API_KEY",
            "ANTHROPIC_API_KEY"
        )
    }
}
