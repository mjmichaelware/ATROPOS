/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.worktree

import java.nio.file.Path

/**
 * ATOM VC8: MANDATORY TRACKING FETCH BEFORE PULL MERGE
 * Path: src/main/kotlin/atropos/core/worktree/TrackingFetchPrePullGate.kt
 *
 * Ensures that local Git tracking branches are explicitly updated via git fetch
 * prior to executing pull and merge commands. Prevents local merge commits against
 * stale tracking branches.
 */
data class PrePullFetchResult(
    val fetchExecuted: Boolean,
    val fetchSuccessful: Boolean,
    val canProceedWithPull: Boolean,
    val remoteRefUpdated: Boolean,
    val message: String
)

class TrackingFetchPrePullGate(
    private val fetchOnPull: Boolean = true
) {
    fun prepareForPull(
        remoteName: String = "origin",
        branchName: String = "main",
        fetchExecutor: (command: List<String>) -> Boolean = { true }
    ): PrePullFetchResult {
        if (!fetchOnPull) {
            return PrePullFetchResult(
                fetchExecuted = false,
                fetchSuccessful = true,
                canProceedWithPull = true,
                remoteRefUpdated = false,
                message = "Pre-pull fetch skipped by configuration"
            )
        }

        val fetchCmd = listOf("git", "fetch", remoteName, branchName)
        val success = fetchExecutor(fetchCmd)

        return if (success) {
            PrePullFetchResult(
                fetchExecuted = true,
                fetchSuccessful = true,
                canProceedWithPull = true,
                remoteRefUpdated = true,
                message = "Successfully synchronized tracking refs for $remoteName/$branchName"
            )
        } else {
            PrePullFetchResult(
                fetchExecuted = true,
                fetchSuccessful = false,
                canProceedWithPull = false,
                remoteRefUpdated = false,
                message = "Failed to fetch remote tracking branch $remoteName/$branchName"
            )
        }
    }

    companion object {
        val defaultGate = TrackingFetchPrePullGate()
    }
}
