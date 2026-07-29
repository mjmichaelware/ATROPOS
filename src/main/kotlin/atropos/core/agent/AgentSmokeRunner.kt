package atropos.core.agent

import atropos.core.AtroposRepoRootLocator
import atropos.core.policy.AgencyDisposition
import atropos.core.policy.ActionActor
import atropos.core.policy.BoundedAgencyGate
import atropos.core.policy.ExecutionPolicyEngine
import atropos.core.policy.VerificationActionProposals
import atropos.core.security.RedactionFilter
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.file.Path
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

data class AgentSmokeExecutionResult(
    val command: String,
    val passed: Boolean,
    val exitCode: Int? = null,
    val durationMillis: Long = 0L,
    val stdout: String = "",
    val stderr: String = "",
    val refusalReason: String? = null
) {
    fun summary(): String = when {
        passed -> "passed (exit ${exitCode ?: "?"}, ${durationMillis} ms)"
        exitCode != null -> "failed (exit ${exitCode}, ${durationMillis} ms)"
        refusalReason != null -> "refused: ${refusalReason.trim()}"
        else -> "failed (exit ${exitCode ?: "?"}, ${durationMillis} ms)"
    }
}

class AgentSmokeRunner(
    private val repoRoot: Path = AtroposRepoRootLocator.resolve(),
    private val timeoutMillis: Long = (System.getenv("ATROPOS_SMOKE_TIMEOUT_SECONDS") ?: "120").toLongOrNull()
        ?.coerceAtLeast(10)?.times(1000) ?: 120_000L,
    private val maxOutputBytes: Int = 48 * 1024,
    private val maxOutputLines: Int = 1_000,
    private val agencyGate: BoundedAgencyGate = BoundedAgencyGate(ExecutionPolicyEngine(repoRoot)),
    private val redactionFilter: RedactionFilter = RedactionFilter()
) {
    fun validate(command: String): String? {
        val trimmed = command.trim()
        if (trimmed.isBlank()) return "smoke command is blank"
        if (trimmed.length > 500) return "smoke command too long"
        if (trimmed.any { it == '\n' || it == '\r' }) return "smoke command must be a single line"

        val lower = trimmed.lowercase()
        if (listOf("&&", "||", ";", "|", "`", "\$(", ">", "<", "\\", "&").any { it in trimmed }) {
            return "smoke command refuses shell chaining or redirects"
        }
        if (
            lower.contains("curl ") ||
                lower.contains("wget ") ||
                lower.contains("ssh ") ||
                lower.contains("scp ") ||
                lower.contains("sftp ") ||
                lower.contains("rsync ") ||
                lower.contains("nc ") ||
                lower.contains("ncat ") ||
                lower.contains("netcat ") ||
                lower.contains("sudo ") ||
                lower.contains("su ") ||
                lower.contains("rm ") ||
                lower.contains("kill ") ||
                lower.contains("pkill ") ||
                lower.contains("git push") ||
                lower.contains("git commit") ||
                lower.contains("git fetch") ||
                lower.contains("git pull") ||
                lower.contains("git reset") ||
                lower.contains("git clean")
        ) {
            return "smoke command refuses dangerous operations"
        }

        val tokens = trimmed.split(Regex("\\s+")).filter { it.isNotBlank() }
        val first = tokens.firstOrNull() ?: return "smoke command is blank"
        val allowed = setOf(
            "test",
            "git",
            "./gradlew",
            "gradlew",
            "printf",
            "cat",
            "grep",
            "sed",
            "awk",
            "ls",
            "find",
            "pwd",
            "wc",
            "head",
            "tail",
            "sort",
            "cmp",
            "diff",
            "true",
            "false",
            "mkdir",
            "touch",
            "cp"
        )

        if (first == "git") {
            val subcommand = tokens.getOrNull(1)?.lowercase().orEmpty()
            val allowedGit = setOf("status", "diff", "log", "rev-parse", "show", "ls-files")
            if (subcommand !in allowedGit) {
                return "smoke command refuses git subcommand: ${subcommand.ifBlank { "missing" }}"
            }
        } else if (first !in allowed && !first.startsWith("./")) {
            return "smoke command refuses unsupported command: $first"
        }

        return null
    }

    /**
     * @param actor who asked. Smoke commands are operator-supplied by default;
     *   an automated run passes its own identity.
     */
    fun run(
        command: String,
        actor: ActionActor = ActionActor.HumanOwner
    ): AgentSmokeExecutionResult {
        val trimmed = command.trim()
        val refusal = validate(trimmed)
        if (refusal != null) {
            return AgentSmokeExecutionResult(
                command = trimmed,
                passed = false,
                refusalReason = refusal
            )
        }

        val tokens = trimmed.split(Regex("\\s+")).filter { it.isNotBlank() }
        // Pre-authorisation: these tokens came from free text, so the gate
        // decides before the process is built. `validate` above still runs
        // first — bounded agency adds an authority, it does not replace the
        // syntactic refusals.
        val decision = agencyGate.evaluate(
            VerificationActionProposals.smoke(tokens, repoRoot, actor)
        )
        if (decision.disposition != AgencyDisposition.ALLOWED) {
            return AgentSmokeExecutionResult(
                command = trimmed,
                passed = false,
                refusalReason = decision.reason
            )
        }
        val startedAt = System.nanoTime()
        val process = try {
            ProcessBuilder(tokens)
                .directory(repoRoot.toFile())
                .apply {
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
            return AgentSmokeExecutionResult(
                command = trimmed,
                passed = false,
                exitCode = -1,
                durationMillis = 0L,
                stdout = "",
                stderr = "",
                refusalReason = "${failure.javaClass.simpleName}: ${failure.message ?: "smoke launch failed"}"
            )
        }

        val pumps = Executors.newFixedThreadPool(2) { task ->
            Thread(task, "atropos-agent-smoke-stream").apply { isDaemon = true }
        }

        val stdout = pumps.submit<CapturedText> { collect(process.inputStream) }
        val stderr = pumps.submit<CapturedText> { collect(process.errorStream) }

        val finished = process.waitFor(timeoutMillis, TimeUnit.MILLISECONDS)
        if (!finished) terminate(process)

        val out = futureResult(stdout)
        val err = futureResult(stderr)
        pumps.shutdownNow()

        val exitCode = if (finished) process.exitValue() else null
        val passed = finished && exitCode == 0

        return AgentSmokeExecutionResult(
            command = trimmed,
            passed = passed,
            exitCode = exitCode ?: -1,
            durationMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt),
            stdout = out.text,
            stderr = err.text,
            refusalReason = if (passed) null else if (!finished) "smoke timed out after $timeoutMillis ms" else null
        )
    }

    private data class CapturedText(
        val text: String,
        val truncated: Boolean
    )

    private fun collect(input: InputStream): CapturedText {
        val captured = ByteArrayOutputStream(minOf(maxOutputBytes, 8192))
        val buffer = ByteArray(8192)
        var lines = 0
        var truncated = false
        var read: Int

        input.use { stream ->
            while (stream.read(buffer).also { read = it } != -1) {
                for (index in 0 until read) {
                    val value = buffer[index]
                    if (captured.size() < maxOutputBytes && lines < maxOutputLines) {
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

    private fun redactSensitiveOutput(text: String): String = redactionFilter.redact(text)
}
