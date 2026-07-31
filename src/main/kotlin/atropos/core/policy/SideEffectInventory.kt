/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.policy

data class SideEffectPath(
    val className: String,
    val methodName: String,
    val description: String,
    val enforcedBy: String
)

object SideEffectInventory {
    val paths = listOf(
        SideEffectPath(
            className = "atropos.core.worktree.IsolatedWorktreeService",
            methodName = "mutate",
            description = "Modifies worktree files and applies patches",
            enforcedBy = "TerritoryEnforcer & BoundedAgencyGate"
        ),
        SideEffectPath(
            className = "atropos.core.provider.adapter.BaseKernelAdapter",
            methodName = "complete",
            description = "Initiates remote network provider API calls",
            enforcedBy = "ExecutionPolicyEngine & PaidGate"
        ),
        SideEffectPath(
            className = "atropos.core.verification.VerifiedCompletionGate",
            methodName = "evaluate",
            description = "Triggers compile gates and artifact audits",
            enforcedBy = "IndependentVerificationGate"
        ),
        SideEffectPath(
            className = "atropos.core.agent.SelfHostAutonomousRunner",
            methodName = "step",
            description = "Executes recursive self-host loop iterations",
            enforcedBy = "SelfHostSafetyHardFailGate"
        )
    )

    fun getEnforcedCallers(): List<SideEffectPath> = paths
}
