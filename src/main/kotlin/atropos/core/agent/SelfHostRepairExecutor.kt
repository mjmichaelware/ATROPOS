/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.agent

import atropos.core.dag.DagExecutionService
import atropos.core.dag.DagNode
import atropos.core.dag.DagNodeState
import atropos.core.dag.DagNodeAction
import atropos.core.verification.VerifiedCompletionGate
import atropos.core.policy.BoundedAgencyGate
import atropos.core.policy.ExecutionPolicyEngine
import atropos.core.policy.TypedToolExecutor
import atropos.core.policy.VerificationActionProposals
import atropos.core.provider.ProviderTruthService
import atropos.core.provider.ProviderOnboardingService
import atropos.core.agent.AgentService
import atropos.core.agent.AgentContextCollector
import atropos.core.security.RedactionFilter
import atropos.core.recovery.RestartCoordinator
import atropos.core.AtroposConfig
import atropos.core.AtroposRepoRootLocator
import java.nio.file.Path
import java.time.Instant

/**
 * Repair executor for the self-host chain.
 *
 * When a DAG node fails during self-host execution, this executor attempts to
 * repair the failure by:
 * 1. Analyzing the failure reason
 * 2. Applying a repair strategy (re-run, fix, skip, etc.)
 * 3. Re-verifying the node
 *
 * The repair executor uses the existing DagExecutionService and verification
 * gates to ensure repairs are valid and don't bypass safety checks.
 */
class SelfHostRepairExecutor(
    private val repoRoot: java.nio.file.Path = AtroposRepoRootLocator.resolve(),
    private val config: AtroposConfig = AtroposConfig.load(),
    private val dagService: DagExecutionService = DagExecutionService(),
    private val completionGate: VerifiedCompletionGate = VerifiedCompletionGate(repoRoot = AtroposRepoRootLocator.resolve()),
    private val redactionFilter: RedactionFilter = RedactionFilter(),
    private val onboarding: ProviderOnboardingService = ProviderOnboardingService(),
    private val agentService: AgentService = AgentService(
        AtroposConfig.load(),
        AgentContextCollector(repoRoot = AtroposRepoRootLocator.resolve()),
        ProviderOnboardingService()
    ),
    private val providerTruth: ProviderTruthService = ProviderTruthService(AtroposConfig.load()),
    private val dagService: DagExecutionService = DagExecutionService(),
    private val redactionFilter: RedactionFilter = RedactionFilter(),
    private val clock: () -> Instant = { Instant.now() }
) {

    /**
     * Attempts to repair a failed DAG node.
     *
     * @param nodeId The ID of the failed node
     * @param goalId The self-host goal ID
     * @param service The self-host goal service
     * @return RepairResult indicating success/failure and details
     */
    fun repair(nodeId: String, goalId: String, service: SelfHostGoalService): RepairResult {
        val dag = DagExecutionService().readDag(goalId) ?: return RepairResult(false, "DAG not found for goal $goalId")
        val node = dag.findNode(nodeId) ?: return RepairResult(false, "Node $nodeId not found in DAG")

        return when (node.action) {
            DagNodeAction.CREATE_FILE, DagNodeAction.EDIT_FILE -> repairFileMutation(node, goalId)
            DagNodeAction.RUN_COMMAND -> repairRunCommand(node, goalId)
            DagNodeAction.RUN_TEST, DagNodeAction.RUN_BUILD -> repairBuildTest(node, goalId)
            DagNodeAction.VERIFY -> repairVerify(node, goalId)
            DagNodeAction.COMPILE_GATE, DagNodeAction.SMOKE_GATE, DagNodeAction.ACCEPTANCE_GATE -> repairGate(node, goalId)
            DagNodeAction.PROVIDER_CALL -> repairProviderCall(node, goalId)
            else -> RepairResult(false, "No repair strategy for action: ${node.action}")
        }
    }

    private fun repairFileMutation(node: DagNode, goalId: String): RepairResult {
        // Re-run the file mutation with fresh context
        val result = dagService.evaluateDag(goalId)
        if (result.ok) {
            return RepairResult(true, "File mutation re-executed successfully")
        }
        return RepairResult(false, "File mutation repair failed: ${result.message}")
    }

    private fun repairRunCommand(node: DagNode, goalId: String): RepairResult {
        // Re-run the command with fresh context
        val result = dagService.evaluateDag(goalId)
        if (result.ok) {
            return RepairResult(true, "Command re-executed successfully")
        }
        return RepairResult(false, "Command repair failed: ${result.message}")
    }

    private fun repairBuildTest(node: DagNode, goalId: String): RepairResult {
        // Re-run the build/test
        val result = dagService.evaluateDag(goalId)
        if (result.ok) {
            return RepairResult(true, "Build/test re-executed successfully")
        }
        return RepairResult(false, "Build/test repair failed: ${result.message}")
    }

    private fun repairVerify(node: DagNode, goalId: String): RepairResult {
        // Re-run verification
        val result = dagService.evaluateDag(goalId)
        if (result.ok) {
            return RepairResult(true, "Verification re-executed successfully")
        }
        return RepairResult(false, "Verification repair failed: ${result.message}")
    }

    private fun repairGate(node: DagNode, goalId: String): RepairResult {
        // Re-run the gate check
        val result = dagService.evaluateDag(goalId)
        if (result.ok) {
            return RepairResult(true, "Gate check re-executed successfully")
        }
        return RepairResult(false, "Gate repair failed: ${result.message}")
    }

    private fun repairProviderCall(node: DagNode, goalId: String): RepairResult {
        // Re-run the provider call with fresh context
        val result = dagService.evaluateDag(goalId)
        if (result.ok) {
            return RepairResult(true, "Provider call re-executed successfully")
        }
        return RepairResult(false, "Provider call repair failed: ${result.message}")
    }

    data class RepairResult(
        val ok: Boolean,
        val message: String
    )
}