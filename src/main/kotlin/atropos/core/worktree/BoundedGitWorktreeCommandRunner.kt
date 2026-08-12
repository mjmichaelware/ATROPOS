package atropos.core.worktree

import java.nio.charset.StandardCharsets
import java.nio.file.Path

/**
 * The only process boundary used by the self-host worktree path.
 * Callers select a typed Git operation; they cannot provide a shell command.
 */
enum class GitWorktreeOperation {
    INIT,
    CHECKOUT_BRANCH,
    ADD_ALL,
    COMMIT,
    ARCHIVE,
    REV_PARSE_BRANCH,
    REV_PARSE_HEAD,
    VERIFY_COMMIT,
    STATUS_PORCELAIN,
    WORKTREE_ADD,
    APPLY_PATCH,
    REVERSE_APPLY_PATCH,
    CHECKOUT_ALL,
    DIFF_NAME_ONLY,
    UNTRACKED_PATHS,
    DIFF_CHECK,
    DIFF_FROM_BASELINE,
    WORKTREE_REMOVE,
    INTENT_TO_ADD,
    PUSH
}

data class GitWorktreeCommandResult(
    val exitCode: Int,
    val output: String
) {
    val ok: Boolean get() = exitCode == 0

    /**
     * Why this git command failed, in git's own words.
     *
     * Every operator-facing failure in the worktree path used to be a fixed
     * string — "merge apply failed", "could not inspect worktree diff" — with
     * git's actual output discarded. That is the difference between a stall an
     * operator can clear in one command and one that looks like the engine is
     * broken: git already says which file, which hunk, and whether the tree was
     * dirty, and none of it reached the screen.
     *
     * The first non-empty line is the useful one; git puts the cause there and
     * follows it with context. The exit code is carried too, because a command
     * that fails with no output at all is itself a distinct symptom.
     *
     * @param subject what was being attempted, in the operator's terms.
     * @param redact applied to the output. Git error text quotes file contents,
     *   and file contents are eventually a credential.
     */
    fun failureReason(subject: String, redact: (String) -> String = { it }): String {
        val firstLine = output.lineSequence()
            .map { it.trim() }
            .firstOrNull { it.isNotEmpty() }
        return if (firstLine == null) {
            "$subject failed (exit=$exitCode) with no output"
        } else {
            "$subject failed (exit=$exitCode): ${redact(firstLine).take(300)}"
        }
    }
}

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
            GitWorktreeOperation.INIT -> listOf("git", "init")
            GitWorktreeOperation.CHECKOUT_BRANCH -> listOf("git", "checkout", "-b", safeBranch(argument))
            GitWorktreeOperation.ADD_ALL -> listOf("git", "add", ".")
            GitWorktreeOperation.COMMIT -> listOf(
                "git", "-c", "user.name=ATROPOS", "-c", "user.email=atropos@localhost",
                "commit", "-m", safeCommitMessage(argument)
            )
            GitWorktreeOperation.ARCHIVE -> listOf(
                "git", "archive", "--format=tar", "--output=${requiredPath(argument)}", "HEAD"
            )
            GitWorktreeOperation.REV_PARSE_BRANCH -> listOf("git", "rev-parse", "--abbrev-ref", "HEAD")
            GitWorktreeOperation.REV_PARSE_HEAD -> listOf("git", "rev-parse", "HEAD")
            GitWorktreeOperation.VERIFY_COMMIT -> listOf("git", "cat-file", "-e", "${safeRevision(argument)}^{commit}")
            GitWorktreeOperation.STATUS_PORCELAIN -> listOf("git", "status", "--porcelain")
            GitWorktreeOperation.WORKTREE_ADD -> listOf(
                "git", "worktree", "add", "--detach", requiredPath(argument), requiredRevision(input)
            )
            GitWorktreeOperation.APPLY_PATCH -> listOf("git", "apply")
            // Reverses a previously applied diff. The self-host mutation gate
            // uses this to undo a merged worktree change that failed
            // verification, so rejection restores the prior tree.
            GitWorktreeOperation.REVERSE_APPLY_PATCH -> listOf("git", "apply", "-R")
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
            GitWorktreeOperation.PUSH -> listOf("git", "push")
        }
        val feedsPatchOnStdin = operation == GitWorktreeOperation.APPLY_PATCH ||
            operation == GitWorktreeOperation.REVERSE_APPLY_PATCH
        return processRunner(command, directory, if (feedsPatchOnStdin) input else null)
    }

    private fun requiredPath(value: String?): String =
        value?.takeIf { it.isNotBlank() } ?: throw IllegalArgumentException("worktree path is required")

    private fun safeCommitMessage(value: String?): String {
        val message = value?.takeIf { it.isNotBlank() } ?: throw IllegalArgumentException("commit message is required")
        require(!message.contains('\n') && !message.contains('\r')) { "commit message must be single-line" }
        return message
    }

    private fun safeBranch(value: String?): String {
        val branch = value?.trim().takeUnless { it.isNullOrBlank() }
            ?: throw IllegalArgumentException("branch name is required")
        require(branch.matches(Regex("[A-Za-z0-9][A-Za-z0-9._/-]*"))) { "invalid branch name" }
        require(!branch.any(Char::isWhitespace) && !branch.contains("..") && !branch.contains("@{")) {
            "invalid branch name"
        }
        require(!branch.startsWith("/") && !branch.endsWith("/") && !branch.startsWith(".") && !branch.endsWith(".")) {
            "invalid branch name"
        }
        require(!branch.any { it in "~^:?*[\\\\" }) { "invalid branch name" }
        return branch
    }

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
