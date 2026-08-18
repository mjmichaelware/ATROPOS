package atropos.core.policy

import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
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
    val launchError: String? = null,
    val totalOutputBytes: Long = 0L,
    val totalOutputLines: Long = 0L,
    val outputSha256: String? = null,
    val stdoutHead: String = "",
    val stdoutTail: String = "",
    val stderrHead: String = "",
    val stderrTail: String = "",
    val stdoutLogPath: Path? = null,
    val stderrLogPath: Path? = null
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
        removeEnvironmentKeys: Set<String> = emptySet(),
        evidenceDirectory: Path? = null,
        standardInput: ByteArray? = null,
        inputRedirect: Path? = null
    ): BoundedProcessResult {
        validate(command, directory, timeoutMillis, maxOutputBytes, maxOutputLines)

        // Every subprocess the engine runs passes through here -- gradle, git,
        // python, the atomizer. Narrating at this one point is what turns a
        // full trace from a dozen summary lines into an account of what the
        // run actually did, without a publish call at each of a hundred sites.
        atropos.core.thinking.Thinking.detail("process", command.joinToString(" "))

        val started = System.nanoTime()
        val process = try {
            launch(command, directory, environment, removeEnvironmentKeys, inputRedirect)
        } catch (failure: Exception) {
            return BoundedProcessResult(
                exitCode = null,
                timedOut = false,
                durationMillis = elapsed(started),
                stdout = "",
                stderr = "",
                outputTruncated = false,
                launchError = "${failure.javaClass.simpleName}: ${failure.message ?: "process launch failed"}"
                    .also { atropos.core.thinking.Thinking.detail("process", "launch failed — $it") }
            )
        }
        if (standardInput == null) {
            process.outputStream.close()
        } else {
            process.outputStream.use { it.write(standardInput) }
        }

        val pumps = Executors.newFixedThreadPool(2) { task ->
            Thread(task, "atropos-bounded-process-stream").apply { isDaemon = true }
        }
        val logDirectory = evidenceDirectory?.also { Files.createDirectories(it) }
        val stdoutPath = logDirectory?.resolve("stdout.log")
        val stderrPath = logDirectory?.resolve("stderr.log")
        val stdout = pumps.submit<Captured> { capture(process.inputStream, maxOutputBytes, maxOutputLines, stdoutPath) }
        val stderr = pumps.submit<Captured> { capture(process.errorStream, maxOutputBytes, maxOutputLines, stderrPath) }
        val finished = process.waitFor(timeoutMillis, TimeUnit.MILLISECONDS)
        if (!finished) terminate(process)
        val out = result(stdout)
        val err = result(stderr)
        pumps.shutdownNow()

        // The outcome, not just the intent. An exit code is the single most
        // useful line in a trace -- `exit=127` on `./gradlew compileKotlin` is
        // "gradle is not runnable here", which reads nothing like a compile
        // failure and was previously invisible.
        val code = if (finished) process.exitValue() else null
        atropos.core.thinking.Thinking.detail(
            "process",
            command.first() + " " +
                (if (finished) "exit=$code" else "timed out") +
                " in ${elapsed(started)}ms" +
                (err.text.lineSequence().firstOrNull { it.isNotBlank() }?.let { " — ${it.take(120)}" } ?: "")
        )

        return BoundedProcessResult(
            exitCode = code,
            timedOut = !finished,
            durationMillis = elapsed(started),
            stdout = out.text,
            stderr = err.text,
            outputTruncated = out.truncated || err.truncated,
            totalOutputBytes = out.totalBytes + err.totalBytes,
            totalOutputLines = out.totalLines + err.totalLines,
            outputSha256 = combinedDigest(out, err, stdoutPath, stderrPath),
            stdoutHead = out.head,
            stdoutTail = out.tail,
            stderrHead = err.head,
            stderrTail = err.tail,
            stdoutLogPath = stdoutPath,
            stderrLogPath = stderrPath
        )
    }

    /** Starts a bounded, repository-scoped process for a caller that owns its lifecycle. */
    fun start(
        command: List<String>,
        directory: Path,
        environment: Map<String, String> = emptyMap(),
        removeEnvironmentKeys: Set<String> = emptySet(),
        inputRedirect: Path? = null
    ): Process {
        validate(command, directory, 1L, 1, 1)
        return launch(command, directory, environment, removeEnvironmentKeys, inputRedirect)
    }

    private fun launch(
        command: List<String>,
        directory: Path,
        environment: Map<String, String>,
        removeEnvironmentKeys: Set<String>,
        inputRedirect: Path?
    ): Process {
        if (inputRedirect == null) {
            return launcher(command, directory, environment, removeEnvironmentKeys)
        }
        return ProcessBuilder(command)
            .directory(directory.toFile())
            .redirectInput(inputRedirect.toFile())
            .redirectErrorStream(false)
            .apply {
                removeEnvironmentKeys.forEach { environment().remove(it) }
                environment.forEach { (key, value) -> environment()[key] = value }
            }
            .start()
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

    private data class Captured(
        val text: String,
        val truncated: Boolean,
        val totalBytes: Long = 0L,
        val totalLines: Long = 0L,
        val digestHex: String = "",
        val head: String = "",
        val tail: String = ""
    )

    private fun capture(input: java.io.InputStream, maximumBytes: Int, maximumLines: Int, logPath: Path?): Captured {
        val bytes = ByteArrayOutputStream(minOf(maximumBytes, 8192))
        val head = ByteArrayOutputStream(4_096)
        val tail = ByteArrayOutputStream(4_096)
        val digest = MessageDigest.getInstance("SHA-256")
        val log = logPath?.let { Files.newOutputStream(it) }
        val buffer = ByteArray(8192)
        var lines = 0L
        var totalBytes = 0L
        var truncated = false
        input.use { stream -> log?.use { output ->
            while (true) {
                val read = stream.read(buffer)
                if (read == -1) break
                for (index in 0 until read) {
                    val value = buffer[index].toInt()
                    digest.update(buffer[index])
                    output.write(value)
                    totalBytes++
                    if (value == '\n'.code) lines++
                    if (head.size() < 4_096) head.write(value)
                    if (tail.size() == 4_096) {
                        val previous = tail.toByteArray()
                        tail.reset()
                        tail.write(previous, 1, previous.size - 1)
                    }
                    tail.write(value)
                    if (bytes.size() < maximumBytes && lines <= maximumLines) {
                        bytes.write(buffer[index].toInt())
                    } else {
                        truncated = true
                    }
                }
            }
        } ?: run {
            while (true) {
                val read = stream.read(buffer)
                if (read == -1) break
                for (index in 0 until read) {
                    val value = buffer[index].toInt()
                    digest.update(buffer[index])
                    totalBytes++
                    if (value == '\n'.code) lines++
                    if (head.size() < 4_096) head.write(value)
                    if (tail.size() == 4_096) {
                        val previous = tail.toByteArray()
                        tail.reset(); tail.write(previous, 1, previous.size - 1)
                    }
                    tail.write(value)
                    if (bytes.size() < maximumBytes && lines <= maximumLines) bytes.write(value) else truncated = true
                }
            }
        }}
        return Captured(bytes.toByteArray().toString(Charsets.UTF_8), truncated, totalBytes, lines,
            digest.digest().toHex(), head.toByteArray().toString(Charsets.UTF_8), tail.toByteArray().toString(Charsets.UTF_8))
    }

    private fun result(future: Future<Captured>): Captured =
        try {
            future.get(5, TimeUnit.SECONDS)
        } catch (_: Exception) {
            future.cancel(true)
            Captured("", true)
        }

    private fun combinedDigest(stdout: Captured, stderr: Captured, stdoutPath: Path?, stderrPath: Path?): String {
        val digest = MessageDigest.getInstance("SHA-256")
        listOf(stdoutPath, stderrPath).forEach { path ->
            if (path != null && Files.exists(path)) Files.newInputStream(path).use { input ->
                val buffer = ByteArray(8192)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    digest.update(buffer, 0, read)
                }
            }
        }
        if (stdoutPath == null && stderrPath == null) {
            digest.update(stdout.text.toByteArray())
            digest.update(stderr.text.toByteArray())
        }
        return digest.digest().toHex()
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

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
        const val MAX_TIMEOUT_MILLIS = 1_800_000L
        const val MAX_OUTPUT_BYTES = 256 * 1024
        const val MAX_OUTPUT_LINES = 4_000
    }
}
