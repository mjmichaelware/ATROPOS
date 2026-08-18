/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.ui

import atropos.cli.ui.design.Role
import atropos.core.scavenge.GitHubScavenger

/**
 * What a scavenge found, and what the operator may do with it.
 *
 * The last line matters as much as the list. This command finds other
 * people's work, and a list of strangers' issues with no statement of what
 * happens next invites the reader to assume ATROPOS is about to act on them.
 * It is not: nothing here writes to anyone's repository.
 */
class ScavengeRenderer(private val theme: TerminalTheme) {

    fun render(candidates: List<GitHubScavenger.Candidate>, width: Int): List<String> {
        val safeWidth = width.coerceAtLeast(MINIMUM_CELLS)
        if (candidates.isEmpty()) {
            return listOf(
                theme.strong("Nothing to scavenge."),
                theme.subdued(
                    "No open issues labelled " +
                        GitHubScavenger.INVITING_LABELS.joinToString(" or ") { "\"$it\"" } +
                        " and no stuck pull requests."
                )
            )
        }

        val lines = mutableListOf<String>()
        lines += theme.paint(Role.BRAND, "WORK PEOPLE ASKED FOR HELP WITH")
        lines += ""

        candidates.groupBy(GitHubScavenger.Candidate::kind).forEach { (kind, group) ->
            lines += theme.strong(
                when (kind) {
                    GitHubScavenger.Kind.INVITED_ISSUE -> "Issues labelled for help"
                    GitHubScavenger.Kind.CONFLICTED_PULL_REQUEST -> "Pull requests that are stuck"
                }
            )
            group.forEach { candidate ->
                lines += "  " + theme.paint(Role.ACCENT_FOCUS, "${candidate.repository}#${candidate.reference}") +
                    "  " + theme.subdued(candidate.signal)
                lines += "    " + TerminalText.ellipsize(candidate.title, safeWidth - 4)
                lines += "    " + theme.path(candidate.url)
            }
            lines += ""
        }

        lines += theme.subdued(
            "Nothing above has been touched. ATROPOS does not fork, comment on, or " +
                "open anything from a scavenge — it finds work, you choose."
        )
        lines += theme.subdued("Pick one, clone it, and drive it with /factory or /self-host.")
        return lines
    }

    private companion object {
        const val MINIMUM_CELLS = 20
    }
}
