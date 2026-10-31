/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli

/**
 * ATOM VC6: DESTRUCTIVE REBASE WARNING SUPPRESSION
 * Path: src/main/kotlin/atropos/cli/GitRebaseWarningPolicy.kt
 *
 * Provides a configuration toggle to silence warning confirmations when performing
 * interactive rebases or force-with-lease updates, intended for experienced developers
 * while defaulting to safe confirmation warnings.
 */
data class RebaseOperationRisk(
    val isInteractive: Boolean,
    val isForceWithLease: Boolean,
    val targetBranch: String,
    val reflogAnchorSha: String?
)

enum class RebaseWarningVerdict {
    PROCEED_SILENTLY,
    REQUIRE_USER_CONFIRMATION,
    BLOCK_DESTRUCTIVE_FORCE_PUSH
}

class GitRebaseWarningPolicy(
    private val ignoreRebaseWarning: Boolean = false,
    private val protectedBranches: Set<String> = setOf("main", "master", "release")
) {
    fun evaluateRebase(operation: RebaseOperationRisk): RebaseWarningVerdict {
        if (protectedBranches.contains(operation.targetBranch)) {
            return RebaseWarningVerdict.BLOCK_DESTRUCTIVE_FORCE_PUSH
        }

        if (ignoreRebaseWarning) {
            return RebaseWarningVerdict.PROCEED_SILENTLY
        }

        return if (operation.isInteractive || operation.isForceWithLease) {
            RebaseWarningVerdict.REQUIRE_USER_CONFIRMATION
        } else {
            RebaseWarningVerdict.PROCEED_SILENTLY
        }
    }

    companion object {
        val defaultPolicy = GitRebaseWarningPolicy()
    }
}
