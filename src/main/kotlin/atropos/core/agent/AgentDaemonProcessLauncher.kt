package atropos.core.agent

import atropos.core.security.RedactionFilter
import java.nio.file.Files
import java.nio.file.Path

/** Shell-free, repository-bounded process boundary for daemon lifecycle tools. */
class AgentDaemonProcessLauncher(
    private val redactionFilter: RedactionFilter = RedactionFilter()
) {
    fun launchForeground(repoRoot: Path, jar: Path, logFile: Path): Process {
        val root = repoRoot.toAbsolutePath().normalize()
        val candidate = jar.toAbsolutePath().normalize()
        val log = logFile.toAbsolutePath().normalize()
        require(candidate.startsWith(root)) { "daemon jar is outside ATROPOS root" }
        require(log.startsWith(root)) { "daemon log is outside ATROPOS root" }
        require(Files.isRegularFile(candidate) && !Files.isSymbolicLink(candidate)) {
            "daemon jar is missing or symlink-backed"
        }
        Files.createDirectories(log.parent)
        val javaBin = Path.of(System.getProperty("java.home"), "bin", "java").toAbsolutePath().normalize()
        require(Files.isRegularFile(javaBin)) { "java executable is unavailable" }
        return ProcessBuilder(javaBin.toString(), "-Xmx256m", "-jar", candidate.toString(), "--agent-daemon-foreground")
            .directory(root.toFile())
            .redirectInput(ProcessBuilder.Redirect.from(Path.of("/dev/null").toFile()))
            .redirectOutput(ProcessBuilder.Redirect.appendTo(log.toFile()))
            .redirectErrorStream(true)
            .apply {
                environment()["ATROPOS_ROOT"] = root.toString()
                environment()["JAVA_HOME"] = System.getenv("JAVA_HOME") ?: Path.of(System.getProperty("java.home")).toString()
                environment().remove("ATROPOS_API_KEY")
                environment().remove("OPENAI_API_KEY")
                environment().remove("GROQ_API_KEY")
            }
            .start()
    }

    fun runWakeTool(tool: String): Result<Int> {
        require(tool == "termux-wake-lock" || tool == "termux-wake-unlock") {
            "unsupported daemon lifecycle tool: ${redactionFilter.redact(tool)}"
        }
        return runCatching { ProcessBuilder(tool).redirectErrorStream(true).start().waitFor() }
    }
}
