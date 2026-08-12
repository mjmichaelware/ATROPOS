package atropos.core.provider

import atropos.core.AtroposRepoRootLocator
import atropos.core.policy.BoundedProcessRunner
import java.io.File
import java.nio.file.Files
import java.time.Instant

data class LocalProbeResult(val id: String, val available: Boolean, val summary: String, val details: String = "")

class LocalToolchainProbe(
    private val workspace: File = AtroposRepoRootLocator.resolve().toFile(),
    private val processRunner: BoundedProcessRunner = BoundedProcessRunner()
) {
    fun probeKotlinc(): LocalProbeResult = runCommand("kotlinc", "-version").toProbe("local.kotlinc", "Kotlin compiler")
    fun probeGit(): LocalProbeResult = runCommand("git", "status", "--short").toProbe("local.git", "Git status")
    fun probeWorkspace(): LocalProbeResult {
        val src = File(workspace, "src/main/kotlin")
        val count = if (src.exists()) src.walkTopDown().count { it.isFile && it.extension == "kt" } else 0
        return LocalProbeResult("local.workspace", src.exists(), if (src.exists()) "workspace source present" else "workspace source missing", "$count Kotlin files")
    }
    fun probeAll(): List<LocalProbeResult> = listOf(probeWorkspace(), probeKotlinc(), probeGit())
    private fun runCommand(vararg command: String): CommandProbe {
        val directory = workspace.toPath().toAbsolutePath().normalize()
        if (!Files.isDirectory(directory)) return CommandProbe(false, "workspace directory missing")
        return try {
            val result = processRunner.run(
                command = command.toList(),
                directory = directory,
                timeoutMillis = 15_000L,
                maxOutputBytes = 16 * 1024,
                maxOutputLines = 200
            )
            val output = ProviderRedactor.redact(
                listOf(result.stdout, result.stderr, result.launchError.orEmpty())
                    .filter { it.isNotBlank() }
                    .joinToString(" ")
            )
            CommandProbe(
                ok = result.exitCode == 0 && !result.timedOut && !result.outputTruncated && result.launchError == null,
                output = output.take(240)
            )
        } catch (failure: Exception) {
            CommandProbe(false, ProviderRedactor.redact(failure.message ?: failure.javaClass.simpleName))
        }
    }
    private fun CommandProbe.toProbe(id: String, label: String) =
        LocalProbeResult(id, ok, if (ok) "$label available" else "$label unavailable", ProviderRedactor.redact(output))
    private data class CommandProbe(val ok: Boolean, val output: String)
}

class LocalStateStore(
    private val root: File = AtroposRepoRootLocator.resolve().resolve(".atropos/state").toFile()
) {
    init { root.mkdirs() }
    fun appendEvent(stream: String, line: String) {
        val safe = stream.replace(Regex("""[^A-Za-z0-9_.-]"""), "_")
        File(root, "$safe.jsonl").appendText(
            """{"ts":"${Instant.now()}","event":"${jsonEscape(ProviderRedactor.redact(line))}"}""" + "\n"
        )
    }
    fun readTail(stream: String, maxLines: Int = 40): List<String> {
        val safe = stream.replace(Regex("""[^A-Za-z0-9_.-]"""), "_")
        val file = File(root, "$safe.jsonl")
        if (!file.exists()) return emptyList()
        return file.readLines().takeLast(maxLines)
    }
    fun health() = LocalProbeResult("local.state", root.exists() && root.isDirectory, "local state root", root.absolutePath)

    private fun jsonEscape(value: String): String = buildString {
        value.forEach { character ->
            when (character) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(character)
            }
        }
    }
}
