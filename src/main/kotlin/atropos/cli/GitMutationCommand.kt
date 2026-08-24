/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli

import java.nio.file.Path

sealed interface GitMutationParse {
    data class Accepted(val command: List<String>, val targetPaths: List<String>) : GitMutationParse
    data class Refused(val message: String) : GitMutationParse
}

/** Parses explicit-confirmation local Git mutations for ShellCommandHandler. */
object GitMutationCommandParser {
    fun parse(tokens: List<String>): GitMutationParse {
        val confirmation = tokens.indexOf("--confirm")
        if (confirmation < 0 || confirmation != tokens.lastIndex - 1 || tokens[confirmation + 1].isBlank()) {
            return GitMutationParse.Refused("git mutation requires explicit --confirm <id>")
        }
        return when (tokens.getOrNull(1)?.lowercase()) {
            "add" -> parseAdd(tokens, confirmation)
            "commit" -> parseCommit(tokens, confirmation)
            "rebase-continue" -> if (confirmation == 2) {
                GitMutationParse.Accepted(listOf("git", "rebase", "--continue"), listOf("."))
            } else GitMutationParse.Refused("usage: /git rebase-continue --confirm <id>")
            else -> GitMutationParse.Refused("usage: /git [status|diff|add|commit|rebase-continue]")
        }
    }

    private fun parseAdd(tokens: List<String>, confirmation: Int): GitMutationParse {
        if (confirmation != 3) return GitMutationParse.Refused("usage: /git add <path> --confirm <id>")
        val path = tokens[2]
        if (!safeRelativePath(path)) return GitMutationParse.Refused("git add path must stay inside the current territory")
        return GitMutationParse.Accepted(listOf("git", "add", "--", path), listOf(path))
    }

    private fun parseCommit(tokens: List<String>, confirmation: Int): GitMutationParse {
        if (confirmation < 3) return GitMutationParse.Refused("usage: /git commit <message> --confirm <id>")
        val message = tokens.subList(2, confirmation).joinToString(" ").trim()
        if (message.isBlank() || message.length > 500) return GitMutationParse.Refused("git commit message must be 1-500 characters")
        return GitMutationParse.Accepted(listOf("git", "commit", "-m", message), listOf("."))
    }

    private fun safeRelativePath(raw: String): Boolean {
        if (raw.isBlank() || raw.startsWith("/") || raw.startsWith("\\") || raw.contains('\u0000')) return false
        val path = runCatching { Path.of(raw) }.getOrNull() ?: return false
        return !path.normalize().any { it.toString() == ".." }
    }
}
