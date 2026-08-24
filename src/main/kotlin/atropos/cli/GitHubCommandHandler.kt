/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli

import atropos.cli.ui.AnsiTerminalEngine
import atropos.core.AtroposConfig
import atropos.core.github.GitHubApiResponse
import atropos.core.github.GitHubBinding
import atropos.core.github.GitHubWriteAuthorization
import atropos.core.security.RedactionFilter

/** Read-only GitHub inspection through the single gated GitHub binding. */
class GitHubCommandHandler(
    private val config: AtroposConfig,
    private val uiEngine: AnsiTerminalEngine,
    private val binding: GitHubBinding = GitHubBinding(),
    private val redactionFilter: RedactionFilter = RedactionFilter()
) {
    fun execute(tokens: List<String>) {
        if (config.runtime.localOnly) {
            uiEngine.renderNotice("github: blocked by local-only mode")
            return
        }
        val operation = tokens.getOrNull(1)?.lowercase()
        val repository = tokens.getOrNull(2)?.split('/')
        if (repository == null || repository.size != 2 || repository.any(String::isBlank)) {
            uiEngine.renderError("usage: /github issues|issue|prs|pr-files|checks|branch-protection <owner/repository> <arg>")
            return
        }
        val owner = repository[0]
        val name = repository[1]
        val response = runCatching {
            when (operation) {
                "issues" -> binding.listIssues(owner, name, page(tokens.getOrNull(3)))
                "issue" -> binding.getIssue(owner, name, number(tokens.getOrNull(3)))
                "prs", "pulls" -> binding.listPullRequests(owner, name, page(tokens.getOrNull(3)))
                "pr-files" -> binding.getPullRequestFiles(owner, name, number(tokens.getOrNull(3)))
                "checks" -> binding.listCheckRuns(owner, name, tokens.getOrNull(3) ?: error("check ref is required"))
                "branch-protection" -> binding.getBranchProtection(owner, name, tokens.getOrNull(3) ?: error("branch is required"))
                "create-issue" -> binding.createIssue(owner, name, body(tokens, 3), authorization(tokens))
                "comment-issue" -> binding.commentIssue(owner, name, number(tokens.getOrNull(3)), body(tokens, 4), authorization(tokens))
                "create-pr" -> binding.createPullRequest(owner, name, body(tokens, 3), authorization(tokens))
                "comment-pr" -> binding.commentPullRequest(owner, name, number(tokens.getOrNull(3)), body(tokens, 4), authorization(tokens))
                "request-review" -> binding.requestPullReview(owner, name, number(tokens.getOrNull(3)), body(tokens, 4), authorization(tokens))
                "create-check" -> binding.createCheckRun(owner, name, body(tokens, 3), authorization(tokens))
                "update-check" -> binding.updateCheckRun(owner, name, tokens.getOrNull(3)?.toLongOrNull()?.takeIf { it > 0 }
                    ?: error("positive check-run id is required"), body(tokens, 4), authorization(tokens))
                else -> error("usage: /github read-command|write-command <owner/repository> ... --confirm <id>")
            }
        }
        response.fold(
            onSuccess = ::render,
            onFailure = { uiEngine.renderError("github: ${redactionFilter.compact(it.message ?: "request refused")}") }
        )
    }

    private fun page(raw: String?): Int = raw?.toIntOrNull()?.coerceAtLeast(1) ?: 1

    private fun number(raw: String?): Int = raw?.toIntOrNull()?.takeIf { it > 0 }
        ?: error("positive issue or pull-request number is required")

    private fun authorization(tokens: List<String>): GitHubWriteAuthorization {
        val marker = tokens.indexOf("--confirm")
        val confirmation = tokens.getOrNull(marker + 1)?.takeIf { marker >= 0 && it.isNotBlank() }
            ?: error("write requires explicit --confirm <id>")
        return GitHubWriteAuthorization(
            operatorId = System.getenv("ATROPOS_OPERATOR_ID")?.takeIf(String::isNotBlank) ?: "cli",
            confirmationId = confirmation
        )
    }

    private fun body(tokens: List<String>, start: Int): String {
        val marker = tokens.indexOf("--confirm")
        val end = if (marker >= start) marker else tokens.size
        return tokens.subList(start, end).joinToString(" ").trim().takeIf(String::isNotBlank)
            ?: error("GitHub write body is required")
    }

    private fun render(response: GitHubApiResponse) {
        uiEngine.renderNotice(
            "github status=${response.status} evidence=${response.evidenceHash}\n" +
                redactionFilter.compact(response.body, MAX_BODY)
        )
    }

    private companion object {
        const val MAX_BODY = 4_000
    }
}
