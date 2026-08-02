package atropos.core.agent

import atropos.core.policy.ActionActor
import atropos.core.policy.ActionProposal
import atropos.core.policy.BoundedAgencyGate
import atropos.core.policy.BoundedProcessRunner
import atropos.core.policy.ExecutionPolicyEngine
import atropos.core.policy.ToolExecutionResult
import atropos.core.policy.TypedToolExecutor
import atropos.core.policy.VerificationActionProposals
import atropos.core.security.RedactionFilter
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest

private const val CANDIDATE_MAX_OUTPUT_CHARS = 16_000
private const val CANDIDATE_MAX_OUTPUT_LINES = 2_000
private const val CANDIDATE_TIMEOUT_MILLIS = 30 * 60 * 1000L

data class SelfHostCandidateJarBuildResult(
    val ok: Boolean,
    val message: String,
    val candidateJar: Path? = null,
    val proposalId: String? = null,
    val failure: AgentExecutionFailure? = null,
    val outputTruncated: Boolean = false,
    val buildEvidence: SelfHostBuildEvidence? = null
) {
    fun evidenceLine(): String =
        "candidate_jar_build ok=$ok proposal=${proposalId ?: "none"} candidate=${candidateJar?.fileName ?: "none"} ${buildEvidence?.compactLine() ?: "evidence=none"} message=${message.replace('\n', ' ').take(240)}"
}

class SelfHostCandidateJarBuilder(
    private val repoRoot: Path,
    private val command: List<String> = listOf("./gradlew", "test", "jar", "--no-daemon"),
    private val expectedJar: Path = repoRoot.resolve("build/libs/ATROPOS.jar").normalize(),
    private val agency: TypedToolExecutor = TypedToolExecutor(BoundedAgencyGate(ExecutionPolicyEngine(repoRoot))),
    private val redactionFilter: RedactionFilter = RedactionFilter(),
    private val processRunner: ((List<String>, Path) -> CommandRun)? = null,
    private val boundedProcessRunner: BoundedProcessRunner = BoundedProcessRunner()
) {
    fun build(goalId: String): SelfHostCandidateJarBuildResult {
        val commandFailure = validateCommand()
        if (commandFailure != null) {
            return SelfHostCandidateJarBuildResult(
                ok = false,
                message = commandFailure,
                failure = AgentExecutionFailure.INVALID_COMMAND
            )
        }
        if (!isWithinRoot(expectedJar)) {
            return SelfHostCandidateJarBuildResult(
                ok = false,
                message = "candidate jar path escapes repository root",
                failure = AgentExecutionFailure.INVALID_COMMAND
            )
        }
        val proposal = VerificationActionProposals.buildTest(
            command = command,
            repoRoot = repoRoot,
            actor = ActionActor.HierarchyNode(role = "self-host", nodeId = goalId)
        )
        var captured: CommandRun? = null
        val result = runCatching {
            agency.execute(proposal) {
                val run = processRunner?.invoke(command, repoRoot) ?: runBoundedProcess(command, proposal.id)
                captured = run
                buildString {
                    append("exit=").append(run.exitCode)
                    append(" timed_out=").append(run.timedOut)
                    if (run.outputTruncated) append(" truncated=true")
                    append('\n').append(redactionFilter.compact(run.output, 2_000))
                }
            }
        }.getOrElse { failure ->
            return SelfHostCandidateJarBuildResult(
                ok = false,
                message = "candidate jar build failed to start: ${redactionFilter.compact(failure.message.orEmpty(), 240)}",
                proposalId = proposal.id,
                failure = AgentExecutionFailure.LAUNCH_FAILED
            )
        }
        if (!result.authorized || !result.executed) {
            return refused(result, proposal)
        }
        val run = captured ?: return SelfHostCandidateJarBuildResult(
            ok = false,
            message = "candidate jar build evidence was not captured",
            proposalId = proposal.id,
            failure = AgentExecutionFailure.EVIDENCE_MISSING
        )
        val evidence = run.evidence.copy(
            displayHead = redactionFilter.redact(run.evidence.displayHead),
            displayTail = redactionFilter.redact(run.evidence.displayTail)
        )
        val outputTruncated = run.outputTruncated
        val exitCode = run.exitCode
        if (exitCode != 0) {
            return SelfHostCandidateJarBuildResult(
                ok = false,
                message = "candidate jar build failed: ${redactionFilter.compact(result.output.orEmpty(), 240)}",
                proposalId = proposal.id,
                failure = if (run.timedOut) {
                    AgentExecutionFailure.TIMEOUT
                } else {
                    AgentExecutionFailure.NONZERO_EXIT
                },
                outputTruncated = outputTruncated,
                buildEvidence = evidence
            )
        }
        if (!Files.isRegularFile(expectedJar) || Files.size(expectedJar) <= 0L) {
            return SelfHostCandidateJarBuildResult(
                ok = false,
                message = "candidate jar build completed but ${expectedJar.fileName} is missing or empty",
                proposalId = proposal.id,
                failure = AgentExecutionFailure.MISSING_ARTIFACT,
                outputTruncated = outputTruncated,
                buildEvidence = evidence
            )
        }
        val jarEvidence = evidence.copy(
            candidateJarPath = expectedJar,
            candidateJarSize = Files.size(expectedJar),
            candidateJarSha256 = sha256(expectedJar),
            proposalId = proposal.id
        )
        return SelfHostCandidateJarBuildResult(
            ok = true,
            message = "candidate jar built: ${expectedJar.fileName}",
            candidateJar = expectedJar,
            proposalId = proposal.id,
            outputTruncated = outputTruncated,
            buildEvidence = jarEvidence
        )
    }

    private fun refused(result: ToolExecutionResult, proposal: ActionProposal): SelfHostCandidateJarBuildResult =
        SelfHostCandidateJarBuildResult(
            ok = false,
            message = "candidate jar build refused: ${result.refusalReason ?: result.disposition.name}",
            proposalId = proposal.id,
            failure = AgentExecutionFailure.POLICY_REFUSED
        )

    private fun validateCommand(): String? {
        if (command.isEmpty()) return "candidate jar build command is empty"
        if (command.first() !in setOf("./gradlew", "gradlew")) {
            return "candidate jar build requires the repository Gradle launcher"
        }
        if (command.drop(1).any { it.isBlank() || it.contains('\n') || it.contains('\r') || it == "--" }) {
            return "candidate jar build command contains an invalid argument"
        }
        if (command.drop(1).none { it == "test" }) return "candidate jar build command must run the test gate"
        if (command.drop(1).none { it == "jar" }) return "candidate jar build command must request jar"
        return null
    }

    private fun isWithinRoot(path: Path): Boolean =
        path.toAbsolutePath().normalize().startsWith(repoRoot.toAbsolutePath().normalize())

    private fun runBoundedProcess(command: List<String>, proposalId: String): CommandRun {
        val evidenceDirectory = repoRoot.resolve(".atropos/self-hosting/build-evidence/${System.currentTimeMillis()}")
        val result = boundedProcessRunner.run(
            command = command,
            directory = repoRoot,
            timeoutMillis = CANDIDATE_TIMEOUT_MILLIS,
            maxOutputBytes = CANDIDATE_MAX_OUTPUT_CHARS,
            maxOutputLines = CANDIDATE_MAX_OUTPUT_LINES,
            removeEnvironmentKeys = sensitiveEnvironmentKeys(),
            evidenceDirectory = evidenceDirectory
        )
        val rawPaths = listOfNotNull(result.stdoutLogPath, result.stderrLogPath)
        val fullLog = evidenceDirectory.resolve("build.log")
        Files.newBufferedWriter(fullLog).use { writer ->
            rawPaths.forEach { path ->
                if (Files.exists(path)) {
                    Files.newBufferedReader(path).useLines { lines ->
                        lines.forEach { line -> writer.append(redactionFilter.redact(line)).append('\n') }
                    }
                }
            }
        }
        rawPaths.forEach { Files.deleteIfExists(it) }
        return CommandRun(
            exitCode = result.exitCode ?: -1,
            output = buildString {
                append(result.stdout)
                append(result.stderr)
                if (result.outputTruncated) append("\ntruncated=true")
                result.launchError?.let { append("launch_error=").append(it) }
            }.take(CANDIDATE_MAX_OUTPUT_CHARS),
            timedOut = result.timedOut,
            outputTruncated = result.outputTruncated,
            evidence = SelfHostBuildEvidence(
                exitCode = result.exitCode,
                timedOut = result.timedOut,
                totalOutputBytes = result.totalOutputBytes,
                totalOutputLines = result.totalOutputLines,
                outputTruncated = result.outputTruncated,
                outputSha256 = result.outputSha256,
                displayHead = redactionFilter.redact(result.stdoutHead + result.stderrHead),
                displayTail = redactionFilter.redact(result.stdoutTail + result.stderrTail),
                fullLogPath = fullLog,
                fullLogSha256 = sha256(fullLog),
                requestedCommand = command,
                proposalId = proposalId
            )
        )
    }

    private fun sha256(path: Path): String = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)).toHex()
    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    private fun sensitiveEnvironmentKeys(): Set<String> = System.getenv().keys.filter { key ->
        val name = key.uppercase()
        name.contains("TOKEN") || name.contains("SECRET") || name.contains("PASSWORD") ||
            name.endsWith("_KEY") || name.contains("CREDENTIAL")
    }.toSet()

    data class CommandRun(
        val exitCode: Int,
        val output: String,
        val timedOut: Boolean = false,
        val outputTruncated: Boolean = false,
        val evidence: SelfHostBuildEvidence = SelfHostBuildEvidence(
            exitCode = exitCode,
            timedOut = timedOut,
            totalOutputBytes = output.toByteArray().size.toLong(),
            totalOutputLines = output.count { it == '\n' }.toLong(),
            outputTruncated = outputTruncated,
            outputSha256 = MessageDigest.getInstance("SHA-256").digest(output.toByteArray()).joinToString("") { "%02x".format(it) },
            displayHead = output.take(4096),
            displayTail = output.takeLast(4096)
        )
    )

}
