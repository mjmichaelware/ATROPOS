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

private const val CANDIDATE_MAX_OUTPUT_CHARS = 16_000
private const val CANDIDATE_MAX_OUTPUT_LINES = 2_000
private const val CANDIDATE_TIMEOUT_MILLIS = 15 * 60 * 1000L

data class SelfHostCandidateJarBuildResult(
    val ok: Boolean,
    val message: String,
    val candidateJar: Path? = null,
    val proposalId: String? = null,
    val failure: AgentExecutionFailure? = null,
    val outputTruncated: Boolean = false
) {
    fun evidenceLine(): String =
        "candidate_jar_build ok=$ok proposal=${proposalId ?: "none"} candidate=${candidateJar?.fileName ?: "none"} message=${message.replace('\n', ' ').take(240)}"
}

class SelfHostCandidateJarBuilder(
    private val repoRoot: Path,
    private val command: List<String> = listOf("./gradlew", "jar"),
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
        val result = runCatching {
            agency.execute(proposal) {
                val run = processRunner?.invoke(command, repoRoot) ?: runBoundedProcess(command)
                "exit=${run.exitCode} timed_out=${run.timedOut}\n${redactionFilter.compact(run.output, 2_000)}"
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
        val exitCode = Regex("""exit=(-?\d+)""").find(result.output.orEmpty())?.groupValues?.getOrNull(1)?.toIntOrNull()
        if (exitCode != 0) {
            return SelfHostCandidateJarBuildResult(
                ok = false,
                message = "candidate jar build failed: ${redactionFilter.compact(result.output.orEmpty(), 240)}",
                proposalId = proposal.id,
                failure = if (result.output.contains("timed_out=true")) {
                    AgentExecutionFailure.TIMEOUT
                } else {
                    AgentExecutionFailure.NONZERO_EXIT
                },
                outputTruncated = result.output.length >= CANDIDATE_MAX_OUTPUT_CHARS
            )
        }
        if (!Files.isRegularFile(expectedJar) || Files.size(expectedJar) <= 0L) {
            return SelfHostCandidateJarBuildResult(
                ok = false,
                message = "candidate jar build completed but ${expectedJar.fileName} is missing or empty",
                proposalId = proposal.id,
                failure = AgentExecutionFailure.MISSING_ARTIFACT
            )
        }
        return SelfHostCandidateJarBuildResult(
            ok = true,
            message = "candidate jar built: ${expectedJar.fileName}",
            candidateJar = expectedJar,
            proposalId = proposal.id,
            outputTruncated = result.output.length >= CANDIDATE_MAX_OUTPUT_CHARS
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
        if (command.drop(1).none { it == "jar" }) return "candidate jar build command must request jar"
        return null
    }

    private fun isWithinRoot(path: Path): Boolean =
        path.toAbsolutePath().normalize().startsWith(repoRoot.toAbsolutePath().normalize())

    private fun runBoundedProcess(command: List<String>): CommandRun {
        val result = boundedProcessRunner.run(
            command = command,
            directory = repoRoot,
            timeoutMillis = CANDIDATE_TIMEOUT_MILLIS,
            maxOutputBytes = CANDIDATE_MAX_OUTPUT_CHARS,
            maxOutputLines = CANDIDATE_MAX_OUTPUT_LINES,
            removeEnvironmentKeys = sensitiveEnvironmentKeys()
        )
        return CommandRun(
            exitCode = result.exitCode ?: -1,
            output = buildString {
                append(result.stdout)
                append(result.stderr)
                result.launchError?.let { append("launch_error=").append(it) }
            }.take(CANDIDATE_MAX_OUTPUT_CHARS),
            timedOut = result.timedOut
        )
    }

    private fun sensitiveEnvironmentKeys(): Set<String> = System.getenv().keys.filter { key ->
        val name = key.uppercase()
        name.contains("TOKEN") || name.contains("SECRET") || name.contains("PASSWORD") ||
            name.endsWith("_KEY") || name.contains("CREDENTIAL")
    }.toSet()

    data class CommandRun(val exitCode: Int, val output: String, val timedOut: Boolean = false)

}
