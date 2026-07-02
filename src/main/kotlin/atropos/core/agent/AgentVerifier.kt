package atropos.core.agent

import atropos.core.AtroposConfig
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

class AgentVerifier(
    private val config: AtroposConfig = AtroposConfig.load(),
    private val collector: AgentContextCollector = AgentContextCollector(),
    private val patchStore: AgentPatchStore = AgentPatchStore(collector.repoRoot),
    private val verificationStore: AgentVerificationStore = AgentVerificationStore(collector.repoRoot),
    private val javaHome: String = System.getenv("JAVA_HOME")?.takeIf { it.isNotBlank() } ?: "/data/data/com.termux/files/usr",
    private val timeoutMillis: Long = 900_000,
    private val maxOutputBytes: Int = 128 * 1024,
    private val maxOutputLines: Int = 3_000
) {
    fun verify(reference: String): AgentVerificationRunResult {
        val patch = resolvePatch(reference)
            ?: return AgentVerificationRunResult(
                patchId = null,
                verificationId = null,
                patchFile = null,
                passed = false,
                refusalReason = refusalForMissingPatch(reference)
            )

        val execution = runVerificationCommand()
        val passed = execution.exitCode == 0 && !execution.timedOut && execution.launchError == null
        val stdout = redactSensitiveOutput(execution.stdout.text)
        val stderr = redactSensitiveOutput(execution.stderr.text)
        val failureReason = when {
            execution.launchError != null -> execution.launchError
            execution.timedOut -> "verification timed out after ${execution.durationMillis} ms"
            execution.exitCode != 0 -> "verification failed with exit code ${execution.exitCode}"
            else -> null
        }
        val commandText = "JAVA_HOME=$javaHome ${execution.command.joinToString(" ")}"
        val record = verificationStore.createRecord(
            patchId = patch.id,
            command = commandText,
            exitCode = execution.exitCode,
            durationMillis = execution.durationMillis,
            changedPaths = patch.extraction.touchedPaths,
            stdout = stdout,
            stderr = stderr,
            passed = passed,
            failureReason = failureReason
        )

        return AgentVerificationRunResult(
            patchId = patch.id,
            verificationId = record.id,
            patchFile = patch.patchFile,
            command = commandText,
            exitCode = execution.exitCode,
            durationMillis = execution.durationMillis,
            changedPaths = patch.extraction.touchedPaths,
            stdout = stdout,
            stderr = stderr,
            passed = passed,
            metaFile = record.metaFile,
            refusalReason = if (passed) null else failureReason
        )
    }

    fun latestVerification(reference: String): AgentVerificationRecord? {
        val patch = resolvePatch(reference) ?: return null
        return verificationStore.latestRecord(patch.id)
    }

    fun latestFailedVerification(reference: String): AgentVerificationRecord? {
        val patch = resolvePatch(reference) ?: return null
        return verificationStore.latestFailedRecord(patch.id)
    }

    fun resolvePatch(reference: String): AgentPatchSnapshot? =
        patchStore.resolvePatchSnapshot(reference)

    fun latestPatchId(): String? = patchStore.latestPatchId()

    private data class VerificationExecution(
        val command: List<String>,
        val exitCode: Int?,
        val timedOut: Boolean,
        val durationMillis: Long,
        val stdout: CapturedText,
        val stderr: CapturedText,
        val launchError: String? = null
    )

    private data class CapturedText(
        val text: String,
        val truncated: Boolean
    )

    private fun runVerificationCommand(): VerificationExecution {
        val started = System.nanoTime()
        val command = listOf("./gradlew", "test", "jar", "--no-daemon")
        val process = try {
            ProcessBuilder(command)
                .directory(collector.repoRoot.toFile())
                .apply {
                    environment()["JAVA_HOME"] = javaHome
                    environment().keys.removeIf { key ->
                        val name = key.uppercase()
                        name.contains("TOKEN") ||
                            name.contains("SECRET") ||
                            name.contains("PASSWORD") ||
                            name.endsWith("_KEY") ||
                            name.contains("CREDENTIAL")
                    }
                }
                .start()
        } catch (failure: Exception) {
            return VerificationExecution(
                command = command,
                exitCode = null,
                timedOut = false,
                durationMillis = elapsed(started),
                stdout = CapturedText("", false),
                stderr = CapturedText("", false),
                launchError = "${failure.javaClass.simpleName}: ${failure.message ?: "verification launch failed"}"
            )
        }

        val pumps = Executors.newFixedThreadPool(2) { task ->
            Thread(task, "atropos-agent-verify-stream").apply { isDaemon = true }
        }

        val stdout = pumps.submit<CapturedText> {
            collect(process.inputStream, maxOutputBytes, maxOutputLines)
        }
        val stderr = pumps.submit<CapturedText> {
            collect(process.errorStream, maxOutputBytes, maxOutputLines)
        }

        val finished = process.waitFor(timeoutMillis, TimeUnit.MILLISECONDS)
        if (!finished) terminate(process)

        val out = futureResult(stdout)
        val err = futureResult(stderr)
        pumps.shutdownNow()

        return VerificationExecution(
            command = command,
            exitCode = if (finished) process.exitValue() else null,
            timedOut = !finished,
            durationMillis = elapsed(started),
            stdout = out,
            stderr = err
        )
    }

    private fun collect(
        input: InputStream,
        maximumBytes: Int,
        maximumLines: Int
    ): CapturedText {
        val captured = ByteArrayOutputStream(minOf(maximumBytes, 8192))
        val buffer = ByteArray(8192)
        var lines = 0
        var truncated = false
        var read: Int

        input.use { stream ->
            while (stream.read(buffer).also { read = it } != -1) {
                for (index in 0 until read) {
                    val value = buffer[index]
                    if (captured.size() < maximumBytes && lines < maximumLines) {
                        captured.write(value.toInt())
                        if (value.toInt() == '\n'.code) lines++
                    } else {
                        truncated = true
                    }
                }
            }
        }

        return CapturedText(
            redactSensitiveOutput(captured.toByteArray().toString(Charsets.UTF_8)),
            truncated
        )
    }

    private fun futureResult(future: Future<CapturedText>): CapturedText =
        try {
            future.get(5, TimeUnit.SECONDS)
        } catch (_: Exception) {
            future.cancel(true)
            CapturedText("", true)
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

    private fun elapsed(started: Long): Long =
        TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)

    private fun refusalForMissingPatch(reference: String): String =
        if (reference.trim().isBlank()) "no patch id exists"
        else "patch not found: ${reference.trim()}"

    private fun redactSensitiveOutput(text: String): String {
        if (text.isBlank()) return text

        val redactions = listOf(
            Regex("""(?i)(api[_-]?key|token|secret|password|credential|client_secret|authorization)\s*[:=]\s*([^\s'"]+)"""),
            Regex("""(?i)(bearer\s+)[A-Za-z0-9._\-+/=]+"""),
            Regex("""(?i)(GITHUB_MODELS_TOKEN|CLOUDFLARE_API_TOKEN|CLOUDFLARE_ACCOUNT_ID|SAMBANOVA_API_KEY|DEEPSEEK_API_KEY|OPENAI_API_KEY|ANTHROPIC_API_KEY|XAI_API_KEY|GROQ_API_KEY)\s*[:=]\s*([^\s'"]+)""")
        )

        var redacted = text
        for (pattern in redactions) {
            redacted = pattern.replace(redacted) { match ->
                val prefix = match.groupValues.getOrNull(1).orEmpty()
                if (prefix.isBlank()) "<redacted>" else "${prefix.trimEnd()}: <redacted>"
            }
        }
        return redacted
    }
}
