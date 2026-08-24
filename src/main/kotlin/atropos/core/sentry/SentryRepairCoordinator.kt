/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.sentry

import atropos.core.AtroposRepoRootLocator
import atropos.core.agent.WorkerCodeProposal
import atropos.core.agent.WorkerCodeProposalService
import atropos.core.evaluation.EvidenceKind
import atropos.core.evaluation.EvidenceStore
import atropos.core.security.RedactionFilter
import atropos.core.territory.TerritoryEnforcer
import java.nio.file.Path

data class SentryRepairContext(
    val issue: SentryIssue,
    val relativeFile: String,
    val lineNumber: Int?,
    val territory: List<String>,
    val evidenceHash: String,
    val goal: String
)

data class SentryRepairResult(
    val context: SentryRepairContext,
    val proposal: WorkerCodeProposal,
    val proposalEvidenceHash: String
)

/** Composes Sentry evidence with the existing worker proposal/gate path. */
class SentryRepairCoordinator(
    private val repoRoot: Path = AtroposRepoRootLocator.resolve(),
    private val evidenceStore: EvidenceStore = EvidenceStore(repoRoot),
    private val workerProposals: WorkerCodeProposalService = WorkerCodeProposalService(repoRoot = repoRoot),
    private val redactionFilter: RedactionFilter = RedactionFilter()
) {
    fun prepare(issue: SentryIssue, declaredTerritory: List<String>): SentryRepairContext {
        require(declaredTerritory.isNotEmpty()) { "Sentry repair requires declared territory" }
        val frame = issue.frames.firstOrNull { it.filename.isNotBlank() }
            ?: error("Sentry issue has no usable stack frame; repair cannot be proposed")
        val relative = mapFrame(frame.filename)
        val outside = TerritoryEnforcer(declaredTerritory).firstOutside(listOf(relative))
        require(outside == null) { "Sentry stack frame is outside declared territory: $relative" }
        val evidenceHash = evidenceStore.put(
            redactionFilter.redact(
                "sentry_issue=${issue.id}\ntitle=${issue.title}\nculprit=${issue.culprit}\nframe=$relative:${frame.lineNumber ?: "?"}"
            ),
            EvidenceKind.RECEIPT
        )
        return SentryRepairContext(
            issue = issue,
            relativeFile = relative,
            lineNumber = frame.lineNumber,
            territory = declaredTerritory,
            evidenceHash = evidenceHash,
            goal = "Repair Sentry issue ${issue.id}: ${issue.title}; culprit=${issue.culprit}; " +
                "top frame=$relative:${frame.lineNumber ?: "?"}; evidence=$evidenceHash"
        )
    }

    fun propose(context: SentryRepairContext, activeProvider: String): SentryRepairResult {
        val proposal = workerProposals.propose(
            workerId = "sentry-${context.issue.id}",
            activeProvider = activeProvider,
            task = context.goal,
            territory = context.territory
        )
        val proposalEvidenceHash = evidenceStore.put(
            redactionFilter.redact(
                "sentry_issue=${context.issue.id}\nproposal_accepted=${proposal.accepted}\n" +
                    "proposal_sha256=${proposal.proposalSha256 ?: "none"}\nreason=${proposal.reason}"
            ),
            EvidenceKind.RECEIPT
        )
        return SentryRepairResult(context, proposal, proposalEvidenceHash)
    }

    private fun mapFrame(raw: String): String {
        var candidate = raw.trim()
            .removePrefix("file://")
            .replace('\\', '/')
        val root = repoRoot.toAbsolutePath().normalize()
        val absolute = runCatching { Path.of(candidate).toAbsolutePath().normalize() }.getOrNull()
        if (absolute != null && absolute.startsWith(root)) {
            candidate = root.relativize(absolute).toString().replace('\\', '/')
        } else {
            candidate = candidate.substringAfterLast("/src/", candidate).let { value ->
                if (value == candidate) candidate.trimStart('/') else "src/$value"
            }
        }
        require(candidate.isNotBlank() && !candidate.startsWith("../") && !candidate.contains("/../")) {
            "Sentry stack frame cannot be mapped inside the repository"
        }
        return candidate
    }
}
