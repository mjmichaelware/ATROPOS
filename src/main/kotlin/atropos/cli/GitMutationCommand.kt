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
        val verb = tokens.getOrNull(1)?.lowercase()
        return when (verb) {
            "add", "stage" -> parseAdd(tokens, confirmation)
            "commit" -> parseCommit(tokens, confirmation)
            "rollback" -> parseRollback(tokens, confirmation)
            "init" -> if (confirmation == 2) {
                GitMutationParse.Accepted(listOf("git", "init"), listOf("."))
            } else GitMutationParse.Refused("usage: /git init --confirm <id>")
            "rebase-continue" -> if (confirmation == 2) {
                GitMutationParse.Accepted(listOf("git", "rebase", "--continue"), listOf("."))
            } else GitMutationParse.Refused("usage: /git rebase-continue --confirm <id>")
            else -> GitMutationParse.Refused("usage: /git [status|diff|add|stage|commit|rollback|init|rebase-continue]")
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

    private fun parseRollback(tokens: List<String>, confirmation: Int): GitMutationParse {
        if (confirmation != 3) return GitMutationParse.Refused("usage: /git rollback <hash> --confirm <id>")
        val targetHash = tokens[2].trim()
        if (!targetHash.matches(Regex("[a-fA-F0-9]{4,64}"))) return GitMutationParse.Refused("invalid CAS or git hash for rollback")
        return GitMutationParse.Accepted(listOf("git", "reset", "--hard", targetHash), listOf("."))
    }

    private fun safeRelativePath(raw: String): Boolean {
        if (raw.isBlank() || raw.startsWith("/") || raw.startsWith("\\") || raw.contains('\u0000')) return false
        val path = runCatching { Path.of(raw) }.getOrNull() ?: return false
        return !path.normalize().any { it.toString() == ".." }
    }
}
