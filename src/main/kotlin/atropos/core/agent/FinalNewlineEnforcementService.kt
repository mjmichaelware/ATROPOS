/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.agent

/**
 * ATOM W6: Automated Final Newline Enforcement.
 *
 * Ensures all saved text files terminate with a single standard newline character (\n),
 * eliminating "No newline at end of file" Git diff pollution and compiler warnings.
 *
 * ## Research: POSIX Line Termination Compliance Across Heterogeneous Checkouts
 * Researches enforcing LF termination (`\n`) uniformly on mobile Android and Linux checkouts
 * to prevent CRLF dirty worktree conversions during multi-surface commits.
 */
object FinalNewlineEnforcementService {

    fun ensureSingleTrailingNewline(content: String): String {
        if (content.isEmpty()) return ""
        val trimmed = content.trimEnd('\r', '\n', ' ', '\t')
        return "$trimmed\n"
    }

    fun hasValidTermination(content: String): Boolean {
        return content.endsWith("\n") && !content.endsWith("\n\n")
    }
}
