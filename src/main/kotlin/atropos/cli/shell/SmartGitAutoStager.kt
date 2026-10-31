/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.shell

/**
 * ATOM VC3: SMART AUTOMATIC STAGING FOR UNSTAGED COMMITS
 * Path: src/main/kotlin/atropos/cli/shell/SmartGitAutoStager.kt
 *
 * Automatically stages all tracked modified files if a commit command is executed
 * when the staging area is empty. Warns the operator if untracked files exist and
 * requires explicit approval before staging new files.
 */
data class GitStatusEvaluation(
    val stagedFiles: List<String>,
    val trackedModifiedFiles: List<String>,
    val untrackedFiles: List<String>
) {
    val isStagingAreaEmpty: Boolean get() = stagedFiles.isEmpty()
    val hasModificationsToAutoStage: Boolean get() = isStagingAreaEmpty && trackedModifiedFiles.isNotEmpty()
}

enum class SmartCommitDecision {
    PROCEED_NORMALLY,
    AUTO_STAGE_TRACKED_AND_COMMIT,
    REQUIRE_CONFIRMATION_FOR_UNTRACKED,
    ABORT_NOTHING_TO_COMMIT
}

data class SmartCommitActionPlan(
    val decision: SmartCommitDecision,
    val filesToAutoStage: List<String>,
    val warningMessage: String? = null
)

class SmartGitAutoStager(
    private val smartCommitEnabled: Boolean = true
) {
    fun evaluateCommitState(status: GitStatusEvaluation): SmartCommitActionPlan {
        if (!smartCommitEnabled) {
            return SmartCommitActionPlan(
                decision = if (status.stagedFiles.isNotEmpty()) SmartCommitDecision.PROCEED_NORMALLY else SmartCommitDecision.ABORT_NOTHING_TO_COMMIT,
                filesToAutoStage = emptyList()
            )
        }

        if (status.stagedFiles.isNotEmpty()) {
            return SmartCommitActionPlan(
                decision = SmartCommitDecision.PROCEED_NORMALLY,
                filesToAutoStage = emptyList()
            )
        }

        if (status.trackedModifiedFiles.isEmpty() && status.untrackedFiles.isEmpty()) {
            return SmartCommitActionPlan(
                decision = SmartCommitDecision.ABORT_NOTHING_TO_COMMIT,
                filesToAutoStage = emptyList(),
                warningMessage = "Nothing to commit (working tree clean)"
            )
        }

        if (status.trackedModifiedFiles.isNotEmpty() && status.untrackedFiles.isNotEmpty()) {
            return SmartCommitActionPlan(
                decision = SmartCommitDecision.REQUIRE_CONFIRMATION_FOR_UNTRACKED,
                filesToAutoStage = status.trackedModifiedFiles,
                warningMessage = "Found ${status.untrackedFiles.size} untracked files. Staging tracked files only; explicit confirmation needed to include untracked."
            )
        }

        return SmartCommitActionPlan(
            decision = SmartCommitDecision.AUTO_STAGE_TRACKED_AND_COMMIT,
            filesToAutoStage = status.trackedModifiedFiles,
            warningMessage = "Auto-staged ${status.trackedModifiedFiles.size} modified files for commit"
        )
    }

    companion object {
        val defaultStager = SmartGitAutoStager()
    }
}
