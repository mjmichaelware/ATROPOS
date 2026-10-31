/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.artifact

/**
 * ATOM VC4: POST-COMMIT AUTOMATED WORKFLOW ACTIONS
 * Path: src/main/kotlin/atropos/core/artifact/PostCommitWorkflowDispatcher.kt
 *
 * Executes configured secondary actions automatically following a successful local
 * Git commit, such as triggering an immediate push to remote tracking branches or
 * launching automated CI validation passes.
 */
enum class PostCommitAction(val configValue: String) {
    NONE("none"),
    PUSH("push"),
    SYNC("sync");

    companion object {
        fun fromConfig(value: String?): PostCommitAction =
            values().firstOrNull { it.configValue.equals(value?.trim(), ignoreCase = true) } ?: NONE
    }
}

data class PostCommitContext(
    val commitSha: String,
    val branchName: String,
    val commitMessage: String,
    val touchedFileCount: Int
)

data class PostCommitExecutionPlan(
    val action: PostCommitAction,
    val commandsToExecute: List<List<String>>,
    val description: String
)

class PostCommitWorkflowDispatcher(
    private val action: PostCommitAction = PostCommitAction.NONE
) {
    fun resolvePostCommitPlan(context: PostCommitContext): PostCommitExecutionPlan {
        return when (action) {
            PostCommitAction.NONE -> PostCommitExecutionPlan(
                action = PostCommitAction.NONE,
                commandsToExecute = emptyList(),
                description = "No secondary post-commit actions configured"
            )
            PostCommitAction.PUSH -> PostCommitExecutionPlan(
                action = PostCommitAction.PUSH,
                commandsToExecute = listOf(
                    listOf("git", "push", "origin", context.branchName)
                ),
                description = "Triggering immediate push of commit ${context.commitSha.take(8)} to origin/${context.branchName}"
            )
            PostCommitAction.SYNC -> PostCommitExecutionPlan(
                action = PostCommitAction.SYNC,
                commandsToExecute = listOf(
                    listOf("git", "pull", "--rebase", "origin", context.branchName),
                    listOf("git", "push", "origin", context.branchName)
                ),
                description = "Executing pull --rebase followed by push for origin/${context.branchName}"
            )
        }
    }

    companion object {
        val defaultDispatcher = PostCommitWorkflowDispatcher()
    }
}
