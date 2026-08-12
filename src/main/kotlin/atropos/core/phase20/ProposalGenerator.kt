/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.phase20

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Instant

data class ProposalDeficiency(
    val proposedBy: String,
    val summary: String,
    val necessity: List<String>,
    val baseline: String,
    val target: String,
    val guardrails: List<String>,
    val territory: List<String>,
    val risk: String,
    val rollback: String,
    val metric: MetricDeclaration,
    val observedAt: Instant
)

/** Builds complete improvement proposals from an observed, evidenced deficiency. */
class ProposalGenerator {
    fun generate(deficiency: ProposalDeficiency): ImprovementProposal {
        require(deficiency.proposedBy.isNotBlank()) { "proposal author must not be blank" }
        require(deficiency.summary.isNotBlank()) { "proposal summary must not be blank" }
        require(deficiency.necessity.isNotEmpty()) { "proposal necessity evidence is required" }
        require(deficiency.metric.isDeclared()) { "proposal metric must declare a baseline and target" }
        return ImprovementProposal(
            id = "prop-${fingerprint(deficiency).take(16)}",
            proposedBy = deficiency.proposedBy,
            summary = deficiency.summary,
            necessity = deficiency.necessity.distinct(),
            baseline = deficiency.baseline,
            target = deficiency.target,
            guardrails = deficiency.guardrails.distinct(),
            territory = deficiency.territory.distinct(),
            risk = deficiency.risk,
            rollback = deficiency.rollback,
            metric = deficiency.metric,
            createdAt = deficiency.observedAt
        )
    }

    private fun fingerprint(deficiency: ProposalDeficiency): String {
        val canonical = listOf(
            deficiency.proposedBy,
            deficiency.summary,
            deficiency.necessity.sorted().joinToString(","),
            deficiency.baseline,
            deficiency.target,
            deficiency.guardrails.sorted().joinToString(","),
            deficiency.territory.sorted().joinToString(","),
            deficiency.risk,
            deficiency.rollback,
            deficiency.metric.toString(),
            deficiency.observedAt.toString()
        ).joinToString("\n")
        return MessageDigest.getInstance("SHA-256")
            .digest(canonical.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }
}
