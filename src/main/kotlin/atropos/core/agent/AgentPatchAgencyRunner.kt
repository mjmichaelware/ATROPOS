package atropos.core.agent

import atropos.core.policy.ActionActor
import atropos.core.policy.ActionProposal
import atropos.core.policy.AgencyDisposition
import atropos.core.policy.PatchActionProposals
import atropos.core.policy.BoundedProcessRunner
import atropos.core.policy.ToolExecutionResult
import atropos.core.policy.TypedToolExecutor
import java.nio.file.Path

class AgentPatchAgencyRunner(
    private val repoRoot: Path,
    private val agency: TypedToolExecutor,
    private val metadataWriter: AgentPatchMetadataWriter,
    private val processRunner: BoundedProcessRunner = BoundedProcessRunner()
) {
    fun runGitApplyCheck(diffFile: Path): AgentPatchCheckResult =
        runThroughAgency(PatchActionProposals.applyCheck(diffFile, repoRoot, patchActor(diffFile)))

    fun runGitApply(diffFile: Path): AgentPatchCheckResult =
        runThroughAgency(PatchActionProposals.apply(diffFile, repoRoot, patchActor(diffFile)))

    fun runGitStatusForPaths(paths: List<String>): String {
        val cleanPaths = paths.map { it.trim() }.filter { it.isNotBlank() }
        if (cleanPaths.isEmpty()) return ""

        return runThroughAgency(
            PatchActionProposals.statusForPaths(cleanPaths, repoRoot, ActionActor.HumanOwner),
            compact = false
        ).output
    }

    private fun patchActor(diffFile: Path): ActionActor =
        ActionActor.HierarchyNode(
            role = "patch",
            nodeId = diffFile.fileName.toString().removeSuffix(".diff")
        )

    private fun runThroughAgency(
        proposal: ActionProposal,
        compact: Boolean = true
    ): AgentPatchCheckResult {
        var executed: AgentPatchCheckResult? = null
        val outcome = agency.execute(proposal) {
            val bounded = processRunner.run(
                command = proposal.command,
                directory = repoRoot,
                timeoutMillis = 60_000L,
                maxOutputBytes = 128 * 1024,
                maxOutputLines = 4_000
            )
            val check = AgentPatchCheckResult(
                passed = bounded.exitCode == 0 && !bounded.timedOut && bounded.launchError == null,
                exitCode = bounded.exitCode ?: 124,
                output = if (compact) metadataWriter.compactOutput(bounded.stdout + bounded.stderr) else bounded.stdout + bounded.stderr,
                disposition = AgencyDisposition.ALLOWED,
                proposalId = proposal.id
            )
            executed = check
            check.output
        }
        return executed ?: refusedCheck(proposal, outcome)
    }

    private fun refusedCheck(proposal: ActionProposal, outcome: ToolExecutionResult): AgentPatchCheckResult =
        AgentPatchCheckResult(
            passed = false,
            exitCode = when (outcome.disposition) {
                AgencyDisposition.APPROVAL_REQUIRED -> EXIT_APPROVAL_REQUIRED
                else -> EXIT_POLICY_BLOCKED
            },
            output = outcome.refusalReason ?: outcome.policyDecision.reason,
            disposition = outcome.disposition,
            proposalId = proposal.id
        )

    private companion object {
        const val EXIT_POLICY_BLOCKED = 126
        const val EXIT_APPROVAL_REQUIRED = 125
    }
}
