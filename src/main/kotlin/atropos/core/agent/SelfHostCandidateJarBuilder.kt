package atropos.core.agent

import atropos.core.policy.ActionActor
import atropos.core.policy.ActionProposal
import atropos.core.policy.BoundedAgencyGate
import atropos.core.policy.ExecutionPolicyEngine
import atropos.core.policy.ToolExecutionResult
import atropos.core.policy.TypedToolExecutor
import atropos.core.policy.VerificationActionProposals
import atropos.core.security.RedactionFilter
import java.nio.file.Files
import java.nio.file.Path

data class SelfHostCandidateJarBuildResult(
    val ok: Boolean,
    val message: String,
    val candidateJar: Path? = null,
    val proposalId: String? = null
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
    private val processRunner: (List<String>, Path) -> CommandRun = ::runSelfHostCandidateJarProcess
) {
    fun build(goalId: String): SelfHostCandidateJarBuildResult {
        val proposal = VerificationActionProposals.buildTest(
            command = command,
            repoRoot = repoRoot,
            actor = ActionActor.HierarchyNode(role = "self-host", nodeId = goalId)
        )
        val result = runCatching {
            agency.execute(proposal) {
                val run = processRunner(command, repoRoot)
                "exit=${run.exitCode}\n${redactionFilter.compact(run.output, 2_000)}"
            }
        }.getOrElse { failure ->
            return SelfHostCandidateJarBuildResult(
                ok = false,
                message = "candidate jar build failed to start: ${redactionFilter.compact(failure.message.orEmpty(), 240)}",
                proposalId = proposal.id
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
                proposalId = proposal.id
            )
        }
        if (!Files.isRegularFile(expectedJar) || Files.size(expectedJar) <= 0L) {
            return SelfHostCandidateJarBuildResult(
                ok = false,
                message = "candidate jar build completed but ${expectedJar.fileName} is missing or empty",
                proposalId = proposal.id
            )
        }
        return SelfHostCandidateJarBuildResult(
            ok = true,
            message = "candidate jar built: ${expectedJar.fileName}",
            candidateJar = expectedJar,
            proposalId = proposal.id
        )
    }

    private fun refused(result: ToolExecutionResult, proposal: ActionProposal): SelfHostCandidateJarBuildResult =
        SelfHostCandidateJarBuildResult(
            ok = false,
            message = "candidate jar build refused: ${result.refusalReason ?: result.disposition.name}",
            proposalId = proposal.id
        )

    data class CommandRun(val exitCode: Int, val output: String)
}

private fun runSelfHostCandidateJarProcess(
    command: List<String>,
    repoRoot: Path
): SelfHostCandidateJarBuilder.CommandRun {
    val process = ProcessBuilder(command)
        .directory(repoRoot.toFile())
        .redirectErrorStream(true)
        .start()
    val output = process.inputStream.bufferedReader().readText()
    val exit = process.waitFor()
    return SelfHostCandidateJarBuilder.CommandRun(exit, output)
}
