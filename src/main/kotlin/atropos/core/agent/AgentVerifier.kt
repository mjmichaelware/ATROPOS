package atropos.core.agent

import atropos.core.AtroposConfig
import atropos.core.memory.LocalMemoryStore
import atropos.core.policy.AgencyDisposition
import atropos.core.policy.ActionActor
import atropos.core.policy.BoundedAgencyGate
import atropos.core.policy.BoundedProcessRunner
import atropos.core.policy.ExecutionPolicyEngine
import atropos.core.policy.VerificationActionProposals
import atropos.core.security.RedactionFilter
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.util.concurrent.TimeUnit

class AgentVerifier(
    private val config: AtroposConfig = AtroposConfig.load(),
    private val collector: AgentContextCollector = AgentContextCollector(),
    private val patchStore: AgentPatchStore = AgentPatchStore(collector.repoRoot),
    private val verificationStore: AgentVerificationStore = AgentVerificationStore(collector.repoRoot),
    /**
     * The running JVM's own home, not one device's layout.
     *
     * This defaulted to a literal Termux path, so on any other host — a laptop, CI,
     * a container, a different Android ROM — verification pointed at a directory
     * that does not exist. `java.home` is set by the JVM that is already executing
     * this code, so it is correct everywhere by construction; JAVA_HOME stays as an
     * explicit operator override.
     */
    private val javaHome: String = System.getenv("JAVA_HOME")?.takeIf { it.isNotBlank() }
        ?: System.getProperty("java.home")?.takeIf { it.isNotBlank() }
        ?: System.getProperty("user.dir"),
    private val timeoutMillis: Long = 900_000,
    private val maxOutputBytes: Int = 128 * 1024,
    private val maxOutputLines: Int = 3_000,
    private val agencyGate: BoundedAgencyGate = BoundedAgencyGate(ExecutionPolicyEngine(collector.repoRoot)),
    private val memoryStore: LocalMemoryStore = LocalMemoryStore(collector.repoRoot.resolve(".atropos/memory").toFile()),
    private val redactionFilter: RedactionFilter = RedactionFilter(),
    private val processRunner: BoundedProcessRunner = BoundedProcessRunner()
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

        val execution = runVerificationCommand(patch.id)
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
        memoryStore.rememberVerification(
            subjectId = record.id,
            title = "verification ${if (passed) "passed" else "failed"}",
            body = buildString {
                appendLine("patch=${patch.id}")
                appendLine("passed=$passed")
                appendLine("exit=${execution.exitCode ?: "none"}")
                appendLine("duration=${execution.durationMillis}")
                appendLine("changed=${patch.extraction.touchedPaths.joinToString(", ").ifBlank { "none" }}")
                appendLine("failure=${failureReason ?: "none"}")
            }.trimEnd(),
            tags = listOf("agent", "verification", if (passed) "passed" else "failed")
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

    private fun runVerificationCommand(patchId: String): VerificationExecution {
        val started = System.nanoTime()
        val command = listOf("./gradlew", "test", "jar", "--no-daemon")
        // Pre-authorisation: the gate decides before the process is built, so a
        // refusal returns with nothing spawned.
        val decision = agencyGate.evaluate(
            VerificationActionProposals.buildTest(
                command,
                collector.repoRoot,
                ActionActor.HierarchyNode(role = "verify", nodeId = patchId)
            )
        )
        if (decision.disposition != AgencyDisposition.ALLOWED) {
            return VerificationExecution(
                command = command,
                exitCode = null,
                timedOut = false,
                durationMillis = elapsed(started),
                stdout = CapturedText("", false),
                stderr = CapturedText("", false),
                launchError = decision.reason
            )
        }
        val environment = System.getenv().filterKeys { key ->
            val name = key.uppercase()
            !(
                name.contains("TOKEN") ||
                    name.contains("SECRET") ||
                    name.contains("PASSWORD") ||
                    name.endsWith("_KEY") ||
                    name.contains("CREDENTIAL")
                )
        } + ("JAVA_HOME" to javaHome)
        val bounded = processRunner.run(
            command = command,
            directory = collector.repoRoot,
            timeoutMillis = timeoutMillis.coerceAtMost(900_000L),
            maxOutputBytes = maxOutputBytes.coerceAtMost(256 * 1024),
            maxOutputLines = maxOutputLines.coerceAtMost(4_000),
            environment = environment
        )

        return VerificationExecution(
            command = command,
            exitCode = bounded.exitCode,
            timedOut = bounded.timedOut,
            durationMillis = bounded.durationMillis,
            stdout = CapturedText(redactSensitiveOutput(bounded.stdout), bounded.outputTruncated),
            stderr = CapturedText(redactSensitiveOutput(bounded.stderr), bounded.outputTruncated),
            launchError = bounded.launchError
        )
    }

    private fun elapsed(started: Long): Long =
        TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)

    private fun refusalForMissingPatch(reference: String): String =
        if (reference.trim().isBlank()) "no patch id exists"
        else "patch not found: ${reference.trim()}"

    private fun redactSensitiveOutput(text: String): String = redactionFilter.redact(text)
}
