/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli

import atropos.cli.ui.AnsiTerminalEngine
import atropos.core.AtroposRepoRootLocator
import atropos.core.sentry.SentryApiClient
import atropos.core.sentry.SentryRepairCoordinator

class SentryCommandHandler(
    private val uiEngine: AnsiTerminalEngine,
    private val client: SentryApiClient = SentryApiClient(),
    private val coordinator: SentryRepairCoordinator = SentryRepairCoordinator(AtroposRepoRootLocator.resolve())
) {
    fun execute(tokens: List<String>) {
        val operation = tokens.getOrNull(1)?.lowercase()
        val issueId = tokens.getOrNull(2)
        if (operation !in setOf("inspect", "propose") || issueId.isNullOrBlank()) {
            uiEngine.renderError("usage: /sentry inspect <issue-id> [--territory path[,path...]] | /sentry propose <issue-id> --provider <id> [--territory path[,path...]]")
            return
        }
        val territory = option(tokens, "--territory")?.split(',')?.map(String::trim)?.filter(String::isNotBlank)
            ?: listOf(".")
        runCatching {
            val issue = client.getIssue(issueId, territory)
            val context = coordinator.prepare(issue, territory)
            if (operation == "inspect") {
                listOf(
                    "sentry issue=${issue.id}",
                    "title=${issue.title}",
                    "frame=${context.relativeFile}:${context.lineNumber ?: "?"}",
                    "territory=${context.territory.joinToString(",")}",
                    "evidence_sha256=${context.evidenceHash}"
                )
            } else {
                val provider = option(tokens, "--provider") ?: error("/sentry propose requires --provider <id>")
                val result = coordinator.propose(context, provider)
                listOf(
                    "sentry proposal issue=${issue.id}",
                    "accepted=${result.proposal.accepted}",
                    "proposal_sha256=${result.proposal.proposalSha256 ?: "none"}",
                    "evidence_sha256=${result.proposalEvidenceHash}",
                    "reason=${result.proposal.reason}"
                )
            }
        }.onSuccess(uiEngine::renderBlock)
            .onFailure { uiEngine.renderError("Sentry operation refused: ${it.message ?: it.javaClass.simpleName}") }
    }

    private fun option(tokens: List<String>, name: String): String? {
        val index = tokens.indexOf(name)
        if (index < 0) return null
        return tokens.getOrNull(index + 1)?.takeIf { !it.startsWith("--") }
    }
}
