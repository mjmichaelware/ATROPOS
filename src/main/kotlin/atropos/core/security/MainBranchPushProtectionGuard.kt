/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.security

/**
 * ATOM VC9: MAIN BRANCH ACCIDENTAL PUSH PROTECTION GUARD
 * Path: src/main/kotlin/atropos/core/security/MainBranchPushProtectionGuard.kt
 *
 * Intercepts outgoing Git push commands targeting protected branches (such as main or master).
 * Blocks direct pushes from autonomous agents or accidental user actions, requiring
 * feature branch creation and pull requests.
 */
data class BranchPushAuthorization(
    val allowed: Boolean,
    val targetBranch: String,
    val isProtected: Boolean,
    val requiresPullRequest: Boolean,
    val reason: String
)

class MainBranchPushProtectionGuard(
    private val branchProtectionEnabled: Boolean = true,
    private val protectedBranches: Set<String> = setOf("main", "master", "release", "production")
) {
    fun authorizePush(
        targetBranch: String,
        isAutonomousAgent: Boolean = false,
        hasBypassPrivilege: Boolean = false
    ): BranchPushAuthorization {
        val normalized = targetBranch.trim().lowercase().removePrefix("origin/")
        val isProtected = protectedBranches.contains(normalized)

        if (!branchProtectionEnabled || !isProtected) {
            return BranchPushAuthorization(
                allowed = true,
                targetBranch = targetBranch,
                isProtected = isProtected,
                requiresPullRequest = false,
                reason = "Push to un-protected branch allowed"
            )
        }

        if (hasBypassPrivilege && !isAutonomousAgent) {
            return BranchPushAuthorization(
                allowed = true,
                targetBranch = targetBranch,
                isProtected = true,
                requiresPullRequest = false,
                reason = "Operator manual bypass authorized"
            )
        }

        return BranchPushAuthorization(
            allowed = false,
            targetBranch = targetBranch,
            isProtected = true,
            requiresPullRequest = true,
            reason = "Direct push to protected branch '$targetBranch' blocked. Create a feature branch and pull request."
        )
    }

    companion object {
        val defaultGuard = MainBranchPushProtectionGuard()
    }
}
