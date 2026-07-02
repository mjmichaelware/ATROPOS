/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.shell

import atropos.core.security.RedactionFilter
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.charset.Charset
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

data class ShellCommandResult(
    val command: List<String>,
    val cwd: String,
    val exitCode: Int,
    val elapsedMs: Long,
    val timedOut: Boolean,
    val output: String
) {
    val passed: Boolean = exitCode == 0 && !timedOut
}

class ShellCommandRunner(
    initialDirectory: Path = Path.of(System.getProperty("user.dir") ?: "."),
    private val timeoutMs: Long = 15_000L,
    private val redactionFilter: RedactionFilter = RedactionFilter()
) {
    private var cwd: File = initialDirectory.toFile().canonicalFile

    fun currentDirectory(): String = cwd.path

    fun changeDirectory(target: String?): ShellCommandResult {
        val rawTarget = target?.trim().orEmpty().ifBlank { "~" }
        val next = resolveDirectory(rawTarget)

        return if (next != null && next.isDirectory) {
            cwd = next.canonicalFile
            ShellCommandResult(
                command = listOf("cd", rawTarget),
                cwd = cwd.path,
                exitCode = 0,
                elapsedMs = 0L,
                timedOut = false,
                output = "cwd: ${cwd.path}"
            )
        } else {
            ShellCommandResult(
                command = listOf("cd", rawTarget),
                cwd = cwd.path,
                exitCode = 1,
                elapsedMs = 0L,
                timedOut = false,
                output = "cd: no such directory: $rawTarget"
            )
        }
    }

    fun list(args: List<String>): ShellCommandResult =
        run(listOf("ls") + args)

    fun gitStatus(): ShellCommandResult =
        run(listOf("git", "status", "--short"))

    fun run(command: List<String>): ShellCommandResult {
        val cleaned = command.filter { it.isNotBlank() }
        if (cleaned.isEmpty()) {
            return ShellCommandResult(
                command = emptyList(),
                cwd = cwd.path,
                exitCode = 2,
                elapsedMs = 0L,
                timedOut = false,
                output = "shell: empty command"
            )
        }

        val started = System.currentTimeMillis()
        val output = ByteArrayOutputStream()

        return try {
            val process = ProcessBuilder(cleaned)
                .directory(cwd)
                .redirectErrorStream(true)
                .start()

            val reader = thread(
                start = true,
                isDaemon = true,
                name = "atropos-shell-output"
            ) {
                process.inputStream.use { input ->
                    val buffer = ByteArray(4096)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        synchronized(output) {
                            if (output.size() < MAX_OUTPUT_BYTES) {
                                val remaining = MAX_OUTPUT_BYTES - output.size()
                                output.write(buffer, 0, minOf(read, remaining))
                            }
                        }
                    }
                }
            }

            val completed = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
            if (!completed) {
                process.destroyForcibly()
            }
            reader.join(1000L)

            val elapsed = System.currentTimeMillis() - started
            val exit = if (completed) process.exitValue() else 124
            ShellCommandResult(
                command = cleaned,
                cwd = cwd.path,
                exitCode = exit,
                elapsedMs = elapsed,
                timedOut = !completed,
                output = cleanOutput(output)
            )
        } catch (failure: Exception) {
            ShellCommandResult(
                command = cleaned,
                cwd = cwd.path,
                exitCode = 127,
                elapsedMs = System.currentTimeMillis() - started,
                timedOut = false,
                output = failure.message ?: failure.javaClass.simpleName
            )
        }
    }

    fun render(result: ShellCommandResult): String {
        val command = result.command.joinToString(" ")
        val status = if (result.timedOut) "timeout" else result.exitCode.toString()
        val body = result.output
            .lineSequence()
            .take(MAX_OUTPUT_LINES)
            .joinToString("\n")
            .ifBlank { "(no output)" }

        return buildString {
            appendLine("shell:")
            appendLine("  cwd: ${result.cwd}")
            appendLine("  command: $command")
            appendLine("  exit: $status")
            appendLine("  elapsed_ms: ${result.elapsedMs}")
            appendLine("  output:")
            body.lines().forEach { appendLine("    $it") }
        }.trimEnd()
    }

    private fun resolveDirectory(target: String): File? {
        val home = System.getProperty("user.home") ?: ""
        val expanded = when {
            target == "~" -> home
            target.startsWith("~/") -> home + target.removePrefix("~")
            else -> target
        }

        val candidate = File(expanded)
        return if (candidate.isAbsolute) candidate else File(cwd, expanded)
    }

    private fun cleanOutput(output: ByteArrayOutputStream): String {
        val text = synchronized(output) {
            output.toByteArray().toString(Charset.defaultCharset())
        }
        val normalized = text
            .replace("\u0000", "")
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .trimEnd()

        return redactionFilter.redact(normalized).take(MAX_RENDER_CHARS)
    }

    private companion object {
        const val MAX_OUTPUT_BYTES = 64 * 1024
        const val MAX_RENDER_CHARS = 12_000
        const val MAX_OUTPUT_LINES = 120
    }
}
