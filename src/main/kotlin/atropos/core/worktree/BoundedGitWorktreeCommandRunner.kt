package atropos.core.worktree

import java.nio.charset.StandardCharsets
import java.nio.file.Path

/**
 * The only process boundary used by the self-host worktree path.
 * Callers select a typed Git operation; they cannot provide a shell command.
 */
enum class GitWorktreeOperation {
    REV_PARSE_BRANCH,
    REV_PARSE_HEAD,
    STATUS_PORCELAIN,
    WORKTREE_ADD,
    APPLY_PATCH,
    CHECKOUT_ALL,
    DIFF_NAME_ONLY,
    DIFF_CHECK,
    DIFF_FROM_BASELINE,
    WORKTREE_REMOVE,
    INTENT_TO_ADD
}

data class GitWorktreeCommandResult(
    val exitCode: Int,
    val output: String
)

class BoundedGitWorktreeCommandRunner(
    private val processRunner: (List<String>, Path, String?) -> GitWorktreeCommandResult = ::runProcess
) {
    fun run(
        operation: GitWorktreeOperation,
        directory: Path,
        argument: String? = null,
        input: String? = null
    ): GitWorktreeCommandResult {
        val command = when (operation) {
            GitWorktreeOperation.REV_PARSE_BRANCH -> listOf("git", "rev-parse", "--abbrev-ref", "HEAD")
            GitWorktreeOperation.REV_PARSE_HEAD -> listOf("git", "rev-parse", "HEAD")
            GitWorktreeOperation.STATUS_PORCELAIN -> listOf("git", "status", "--porcelain")
            GitWorktreeOperation.WORKTREE_ADD -> listOf(
                "git", "worktree", "add", "--detach", requiredPath(argument), requiredRevision(input)
            )
            GitWorktreeOperation.APPLY_PATCH -> listOf("git", "apply")
            GitWorktreeOperation.CHECKOUT_ALL -> listOf("git", "checkout", "--", ".")
            GitWorktreeOperation.DIFF_NAME_ONLY -> listOf(
                "git", "diff", "--name-only", safeRevision(argument), "--", safeRelativePath(input)
            )
            GitWorktreeOperation.DIFF_CHECK -> listOf("git", "diff", "--check")
            GitWorktreeOperation.DIFF_FROM_BASELINE -> listOf(
                "git", "diff", safeRevision(argument), "--", "."
            )
            GitWorktreeOperation.WORKTREE_REMOVE -> listOf(
                "git", "worktree", "remove", "--force", requiredPath(argument)
            )
            GitWorktreeOperation.INTENT_TO_ADD -> listOf(
                "git", "add", "-N", requiredRelativePath(argument)
            )
        }
        return processRunner(command, directory, if (operation == GitWorktreeOperation.APPLY_PATCH) input else null)
    }

    private fun requiredPath(value: String?): String =
        value?.takeIf { it.isNotBlank() } ?: throw IllegalArgumentException("worktree path is required")

    private fun requiredRevision(value: String?): String =
        value?.takeIf { it.isNotBlank() && !it.any(Char::isWhitespace) && it != "--" }
            ?: throw IllegalArgumentException("baseline revision is required")

    private fun safeRevision(value: String?): String {
        val trimmed = value?.trim()
        if (trimmed.isNullOrBlank()) return "HEAD"
        require(!trimmed.any(Char::isWhitespace) && trimmed != "--" && !trimmed.contains(";")) {
            "invalid revision argument"
        }
        return trimmed
    }

    private fun safeRelativePath(value: String?): String {
        val path = value?.trim().takeUnless { it.isNullOrBlank() } ?: "."
        require(!path.startsWith("/") && !path.contains("..")) {
            "safe relative worktree path is required"
        }
        return path
    }

    private fun requiredRelativePath(value: String?): String {
        val path = value?.trim().orEmpty()
        require(path.isNotBlank()) { "safe relative worktree path is required" }
        return safeRelativePath(path)
    }

    private companion object {
        fun runProcess(
            command: List<String>,
            directory: Path,
            input: String?
        ): GitWorktreeCommandResult = runCatching {
            val process = ProcessBuilder(command)
                .directory(directory.toFile())
                .redirectErrorStream(true)
                .start()
            input?.let {
                process.outputStream.write(it.toByteArray(StandardCharsets.UTF_8))
                process.outputStream.close()
            }
            val output = process.inputStream.bufferedReader().readText()
            GitWorktreeCommandResult(process.waitFor(), output)
        }.getOrElse { failure ->
            GitWorktreeCommandResult(1, failure.message ?: failure.javaClass.simpleName)
        }
    }
}
