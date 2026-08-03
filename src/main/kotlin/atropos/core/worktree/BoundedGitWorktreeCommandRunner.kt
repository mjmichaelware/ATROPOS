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
    UNTRACKED_PATHS,
    DIFF_CHECK,
    DIFF_FROM_BASELINE,
    WORKTREE_REMOVE,
    INTENT_TO_ADD,

    /**
     * Undo an already-applied patch.
     *
     * Needed because a self-host mutation is merged into the real working tree
     * before it is compiled. When verification then fails, the worktree that
     * produced the change has already been removed, so `CHECKOUT_ALL` there has
     * nothing left to revert — the only way back is to reverse the same diff in
     * the repository it landed in.
     */
    REVERSE_APPLY_PATCH,

    /**
     * Stage exactly one declared path.
     *
     * One path per invocation on purpose. `git add -A` / `git add .` would sweep
     * whatever else the operator happens to have in the tree into ATROPOS's own
     * commit, which turns a durability claim about a verified mutation into a
     * claim about unrelated work nobody verified.
     */
    STAGE_PATH,

    /** Read back what is actually in the index, so "staged=N" is observed rather than assumed. */
    STAGED_PATHS,

    /**
     * Commit with the message in `argument` and a newline-separated pathspec in `input`.
     *
     * The trailing pathspec puts the commit in Git's `--only` mode: content
     * outside the named paths stays staged and uncommitted rather than riding
     * along.
     */
    COMMIT_SCOPED_PATHS,

    /** Push one branch to one named remote. `argument` is the remote, `input` the branch. */
    PUSH_BRANCH
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
            GitWorktreeOperation.UNTRACKED_PATHS -> listOf(
                "git", "ls-files", "--modified", "--others", "--exclude-standard"
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
            GitWorktreeOperation.REVERSE_APPLY_PATCH -> listOf("git", "apply", "--reverse")
            GitWorktreeOperation.STAGE_PATH -> listOf(
                "git", "add", "--", requiredRelativePath(argument)
            )
            GitWorktreeOperation.STAGED_PATHS -> listOf("git", "diff", "--cached", "--name-only")
            GitWorktreeOperation.COMMIT_SCOPED_PATHS -> listOf(
                "git", "commit", "-m", requiredCommitMessage(argument), "--"
            ) + requiredRelativePaths(input)
            GitWorktreeOperation.PUSH_BRANCH -> listOf(
                "git", "push", requiredGitToken(argument, "push remote"), requiredGitToken(input, "push branch")
            )
        }
        val patchOperations = setOf(GitWorktreeOperation.APPLY_PATCH, GitWorktreeOperation.REVERSE_APPLY_PATCH)
        return processRunner(command, directory, if (operation in patchOperations) input else null)
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
        require(!path.startsWith("-")) { "safe relative worktree path must not look like an option" }
        return safeRelativePath(path)
    }

    private fun requiredRelativePaths(value: String?): List<String> {
        val paths = value?.lineSequence()?.map { it.trim() }?.filter { it.isNotBlank() }?.toList().orEmpty()
        require(paths.isNotEmpty()) { "at least one scoped path is required" }
        require(paths.size <= MAX_SCOPED_PATHS) { "scoped path list exceeds the bounded argument budget" }
        return paths.map(::requiredRelativePath)
    }

    private fun requiredCommitMessage(value: String?): String {
        val message = value?.takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("commit message is required")
        require(!message.startsWith("-")) { "commit message must not look like an option" }
        require(message.length <= MAX_COMMIT_MESSAGE_CHARS) { "commit message exceeds the bounded argument budget" }
        return message
    }

    private fun requiredGitToken(value: String?, label: String): String {
        val token = value?.trim().orEmpty()
        require(token.isNotBlank() && !token.any(Char::isWhitespace)) { "$label is required" }
        require(!token.startsWith("-") && token != "--" && !token.contains(";")) { "$label is not a safe git token" }
        return token
    }

    private companion object {
        /** Keeps `git commit -- <paths>` inside the bounded runner's argument budget. */
        const val MAX_SCOPED_PATHS = 40
        const val MAX_COMMIT_MESSAGE_CHARS = 4_096

        fun runProcess(
            command: List<String>,
            directory: Path,
            input: String?
        ): GitWorktreeCommandResult = runCatching {
            val process = ProcessBuilder(command)
                .directory(directory.toFile())
                .redirectErrorStream(true)
                .start()
            input?.let { process.outputStream.write(it.toByteArray(StandardCharsets.UTF_8)) }
            // Git commands that do not consume input still need EOF. Leaving
            // this pipe open can suspend commands such as worktree removal.
            process.outputStream.close()
            val output = process.inputStream.bufferedReader().readText()
            GitWorktreeCommandResult(process.waitFor(), output)
        }.getOrElse { failure ->
            GitWorktreeCommandResult(1, failure.message ?: failure.javaClass.simpleName)
        }
    }
}
