package atropos.core.agent

import atropos.core.policy.ActionActor
import atropos.core.policy.ActionProposal
import atropos.core.policy.AgencyDisposition
import atropos.core.policy.PatchActionProposals
import atropos.core.policy.BoundedProcessRunner
import atropos.core.policy.ToolExecutionResult
import atropos.core.policy.TypedToolExecutor
import atropos.core.security.ProtectedPathPolicy
import atropos.core.security.ProtectedPathResult
import java.nio.file.Path

class AgentPatchAgencyRunner(
    private val repoRoot: Path,
    private val agency: TypedToolExecutor,
    private val metadataWriter: AgentPatchMetadataWriter,
    private val processRunner: BoundedProcessRunner = BoundedProcessRunner(),
    private val protectedPathPolicy: ProtectedPathPolicy = ProtectedPathPolicy()
) {
    fun runGitApplyCheck(diffFile: Path): AgentPatchCheckResult {
        val extraction = atropos.core.agent.AgentPatchExtractor().extract(
            java.nio.file.Files.readString(diffFile)
        ) ?: return AgentPatchCheckResult(
            passed = false,
            exitCode = 1,
            output = "no valid diff to check",
            disposition = AgencyDisposition.POLICY_BLOCKED
        )
        val pathCheck = protectedPathPolicy.checkPaths(extraction.touchedPaths)
        if (pathCheck is ProtectedPathResult.Protected) {
            return AgentPatchCheckResult(
                passed = false,
                exitCode = 1,
                output = "protected path: ${pathCheck.reason}",
                disposition = AgencyDisposition.POLICY_BLOCKED
            )
        }
        return runThroughAgency(PatchActionProposals.applyCheck(diffFile, repoRoot, patchActor(diffFile)))
    }

    fun runGitApply(diffFile: Path): AgentPatchCheckResult {
        val extraction = atropos.core.agent.AgentPatchExtractor().extract(
            java.nio.file.Files.readString(diffFile)
        ) ?: return AgentPatchCheckResult(
            passed = false,
            exitCode = 1,
            output = "no valid diff to apply",
            disposition = AgencyDisposition.POLICY_BLOCKED
        )
        val pathCheck = protectedPathPolicy.checkPaths(extraction.touchedPaths)
        if (pathCheck is ProtectedPathResult.Protected) {
            return AgentPatchCheckResult(
                passed = false,
                exitCode = 1,
                output = "protected path: ${pathCheck.reason}",
                disposition = AgencyDisposition.POLICY_BLOCKED
            )
        }
        return runThroughAgency(PatchActionProposals.apply(diffFile, repoRoot, patchActor(diffFile)))
    }

    fun runGitStatusForPaths(paths: List<String>): String {
        val cleanPaths = paths.map { it.trim() }.filter { it.isNotBlank() }
        if (cleanPaths.isEmpty()) return ""

        val pathCheck = protectedPathPolicy.checkPaths(cleanPaths)
        if (pathCheck is ProtectedPathResult.Protected) {
            return "protected path: ${pathCheck.reason}"
        }

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
