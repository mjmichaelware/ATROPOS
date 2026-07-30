package atropos.core.policy

import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

data class BoundedProcessResult(
    val exitCode: Int?,
    val timedOut: Boolean,
    val durationMillis: Long,
    val stdout: String,
    val stderr: String,
    val outputTruncated: Boolean,
    val launchError: String? = null
)

/**
 * Execution mechanics only. Authorization remains with BoundedAgencyGate and
 * callers must invoke this runner only after a typed proposal is allowed.
 */
class BoundedProcessRunner(
    private val launcher: (List<String>, Path, Map<String, String>, Set<String>) -> Process = { command, directory, environmentVariables, removedEnvironmentKeys ->
        ProcessBuilder(command)
            .directory(directory.toFile())
            .redirectErrorStream(false)
            .apply {
                removedEnvironmentKeys.forEach { environment().remove(it) }
                environmentVariables.forEach { (key, value) -> environment()[key] = value }
            }
            .start()
    }
) {
    fun run(
        command: List<String>,
        directory: Path,
        timeoutMillis: Long,
        maxOutputBytes: Int,
        maxOutputLines: Int,
        environment: Map<String, String> = emptyMap(),
        removeEnvironmentKeys: Set<String> = emptySet()
    ): BoundedProcessResult {
        validate(command, directory, timeoutMillis, maxOutputBytes, maxOutputLines)
        val started = System.nanoTime()
        val process = try {
            launcher(command, directory, environment, removeEnvironmentKeys)
        } catch (failure: Exception) {
            return BoundedProcessResult(
                exitCode = null,
                timedOut = false,
                durationMillis = elapsed(started),
                stdout = "",
                stderr = "",
                outputTruncated = false,
                launchError = "${failure.javaClass.simpleName}: ${failure.message ?: "process launch failed"}"
            )
        }
        process.outputStream.close()

        val pumps = Executors.newFixedThreadPool(2) { task ->
            Thread(task, "atropos-bounded-process-stream").apply { isDaemon = true }
        }
        val stdout = pumps.submit<Captured> { capture(process.inputStream, maxOutputBytes, maxOutputLines) }
        val stderr = pumps.submit<Captured> { capture(process.errorStream, maxOutputBytes, maxOutputLines) }
        val finished = process.waitFor(timeoutMillis, TimeUnit.MILLISECONDS)
        if (!finished) terminate(process)
        val out = result(stdout)
        val err = result(stderr)
        pumps.shutdownNow()
        return BoundedProcessResult(
            exitCode = if (finished) process.exitValue() else null,
            timedOut = !finished,
            durationMillis = elapsed(started),
            stdout = out.text,
            stderr = err.text,
            outputTruncated = out.truncated || err.truncated
        )
    }

    private fun validate(
        command: List<String>,
        directory: Path,
        timeoutMillis: Long,
        maxOutputBytes: Int,
        maxOutputLines: Int
    ) {
        require(command.isNotEmpty() && command.size <= MAX_ARGUMENTS) { "bounded process requires 1-$MAX_ARGUMENTS arguments" }
        require(command.all { it.isNotBlank() && it.length <= MAX_ARGUMENT_LENGTH }) {
            "bounded process arguments must be non-blank and limited"
        }
        require(timeoutMillis in 1..MAX_TIMEOUT_MILLIS) { "bounded process timeout is outside allowed range" }
        require(maxOutputBytes in 1..MAX_OUTPUT_BYTES) { "bounded process output limit is outside allowed range" }
        require(maxOutputLines in 1..MAX_OUTPUT_LINES) { "bounded process line limit is outside allowed range" }
        val normalized = directory.toAbsolutePath().normalize()
        require(Files.isDirectory(normalized)) { "bounded process cwd must be an existing directory" }
    }

    private data class Captured(val text: String, val truncated: Boolean)

    private fun capture(input: java.io.InputStream, maximumBytes: Int, maximumLines: Int): Captured {
        val bytes = ByteArrayOutputStream(minOf(maximumBytes, 8192))
        val buffer = ByteArray(8192)
        var lines = 0
        var truncated = false
        input.use { stream ->
            while (true) {
                val read = stream.read(buffer)
                if (read == -1) break
                for (index in 0 until read) {
                    if (bytes.size() < maximumBytes && lines < maximumLines) {
                        bytes.write(buffer[index].toInt())
                        if (buffer[index].toInt() == '\n'.code) lines++
                    } else {
                        truncated = true
                    }
                }
            }
        }
        return Captured(bytes.toByteArray().toString(Charsets.UTF_8), truncated)
    }

    private fun result(future: Future<Captured>): Captured =
        try {
            future.get(5, TimeUnit.SECONDS)
        } catch (_: Exception) {
            future.cancel(true)
            Captured("", true)
        }

    private fun terminate(process: Process) {
        process.toHandle().descendants().forEach { it.destroy() }
        process.destroy()
        if (!process.waitFor(250, TimeUnit.MILLISECONDS)) {
            process.toHandle().descendants().forEach { it.destroyForcibly() }
            process.destroyForcibly()
            process.waitFor(1, TimeUnit.SECONDS)
        }
    }

    private fun elapsed(started: Long): Long = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)

    private companion object {
        const val MAX_ARGUMENTS = 64
        const val MAX_ARGUMENT_LENGTH = 8_192
        const val MAX_TIMEOUT_MILLIS = 900_000L
        const val MAX_OUTPUT_BYTES = 256 * 1024
        const val MAX_OUTPUT_LINES = 4_000
    }
}
