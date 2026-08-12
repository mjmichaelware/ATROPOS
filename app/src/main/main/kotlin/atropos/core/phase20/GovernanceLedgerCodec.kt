/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.phase20

import atropos.core.security.RedactionFilter
import java.time.Instant

/**
 * The durable line format for the Phase 20 ledger.
 *
 * Separate from the store for the same reason the approval codec is: the wire
 * shape is testable without a filesystem, and durability stays a single
 * concern. Every value is redacted on the way out — a proposal's summary and
 * rollback are free text the system generated about its own source, and that
 * text can quote a config file.
 *
 * Decoding is strict. A half-written record is refused rather than filled in
 * with defaults, because a proposal with a silently-blank rollback would fail
 * §20.6's completeness check for the wrong reason — the field would be present
 * and empty, and the operator would be told the proposal is incomplete rather
 * than that its record is damaged.
 */
class GovernanceLedgerCodec(
    private val redactionFilter: RedactionFilter = RedactionFilter()
) {

    fun encodeProposal(proposal: ImprovementProposal): String = buildString {
        field("kind", "proposal")
        field("id", proposal.id)
        field("proposedBy", proposal.proposedBy)
        field("summary", proposal.summary)
        field("necessity", proposal.necessity.joinToString(LIST))
        field("baseline", proposal.baseline)
        field("target", proposal.target)
        field("guardrails", proposal.guardrails.joinToString(LIST))
        field("territory", proposal.territory.joinToString(LIST))
        field("risk", proposal.risk)
        field("rollback", proposal.rollback)
        field("metricName", proposal.metric.name)
        field("metricBaseline", proposal.metric.baselineValue.toString())
        field("metricTarget", proposal.metric.targetValue.toString())
        field("metricLowerIsBetter", proposal.metric.lowerIsBetter.toString())
        field("createdAt", proposal.createdAt.toString())
        field("state", proposal.state.name)
        field("failureCount", proposal.failureCount.toString())
    }.trimEnd()

    fun decodeProposal(line: String): ImprovementProposal? {
        val f = fields(line)
        if (f["kind"] != "proposal") return null
        val id = f["id"]?.takeIf { it.isNotBlank() } ?: return null
        val createdAt = f["createdAt"]?.let { runCatching { Instant.parse(it) }.getOrNull() } ?: return null
        val state = ProposalState.entries.firstOrNull { it.name == f["state"] } ?: return null
        val metricBaseline = f["metricBaseline"]?.toDoubleOrNull() ?: return null
        val metricTarget = f["metricTarget"]?.toDoubleOrNull() ?: return null
        val lowerIsBetter = f["metricLowerIsBetter"]?.toBooleanStrictOrNull() ?: return null

        return ImprovementProposal(
            id = id,
            proposedBy = f["proposedBy"].orEmpty(),
            summary = f["summary"].orEmpty(),
            necessity = list(f["necessity"]),
            baseline = f["baseline"].orEmpty(),
            target = f["target"].orEmpty(),
            guardrails = list(f["guardrails"]),
            territory = list(f["territory"]),
            risk = f["risk"].orEmpty(),
            rollback = f["rollback"].orEmpty(),
            metric = MetricDeclaration(
                name = f["metricName"].orEmpty(),
                baselineValue = metricBaseline,
                targetValue = metricTarget,
                lowerIsBetter = lowerIsBetter
            ),
            createdAt = createdAt,
            state = state,
            failureCount = f["failureCount"]?.toIntOrNull() ?: 0
        )
    }

    fun encodeAmendment(amendment: AuthorityAmendment): String = buildString {
        field("kind", "amendment")
        field("id", amendment.id)
        field("proposalId", amendment.proposalId)
        field("sha256", amendment.sha256)
        field("supersedes", amendment.supersedes)
        field("acceptedBy", amendment.acceptedBy)
        field("acceptedAt", amendment.acceptedAt.toString())
        field("evidence", amendment.evidenceHashes.joinToString(LIST))
    }.trimEnd()

    fun decodeAmendment(line: String): AuthorityAmendment? {
        val f = fields(line)
        if (f["kind"] != "amendment") return null
        val id = f["id"]?.takeIf { it.isNotBlank() } ?: return null
        val acceptedAt = f["acceptedAt"]?.let { runCatching { Instant.parse(it) }.getOrNull() } ?: return null
        val sha = f["sha256"]?.takeIf { it.isNotBlank() } ?: return null
        // §20.8: an amendment with no superseded hash cannot be placed against
        // the authority it modifies, which leaves it unreadable as authority.
        val supersedes = f["supersedes"]?.takeIf { it.isNotBlank() } ?: return null

        return AuthorityAmendment(
            id = id,
            proposalId = f["proposalId"].orEmpty(),
            sha256 = sha,
            supersedes = supersedes,
            acceptedBy = f["acceptedBy"].orEmpty(),
            acceptedAt = acceptedAt,
            evidenceHashes = list(f["evidence"])
        )
    }

    fun encodeObservation(period: ObservationPeriod): String = buildString {
        field("kind", "observation")
        field("subsystem", period.subsystem)
        field("startedAt", period.startedAt.toString())
        field("durationSeconds", period.durationSeconds.toString())
    }.trimEnd()

    fun decodeObservation(line: String): ObservationPeriod? {
        val f = fields(line)
        if (f["kind"] != "observation") return null
        val subsystem = f["subsystem"]?.takeIf { it.isNotBlank() } ?: return null
        val startedAt = f["startedAt"]?.let { runCatching { Instant.parse(it) }.getOrNull() } ?: return null
        val duration = f["durationSeconds"]?.toLongOrNull() ?: return null
        return ObservationPeriod(subsystem, startedAt, duration)
    }

    /** The record kind, so a reader can route a line without decoding it three times. */
    fun kindOf(line: String): String? = fields(line)["kind"]

    private fun fields(line: String): Map<String, String> =
        line.split(SEP)
            .mapNotNull { part ->
                val key = part.substringBefore('=', "")
                if (key.isBlank()) null else key to unescape(part.substringAfter('=', ""))
            }
            .toMap()

    private fun list(raw: String?): List<String> =
        raw.orEmpty().split(LIST).filter { it.isNotBlank() }

    private fun StringBuilder.field(key: String, value: String) {
        append(key).append('=').append(escape(redactionFilter.redact(value))).append(SEP)
    }

    private fun escape(value: String): String = value
        .replace("\\", "\\\\")
        .replace(SEP, "\\u0009")
        .replace("\n", "\\n")
        .replace(LIST, "\\u001f")

    private fun unescape(value: String): String = value
        .replace("\\u001f", LIST)
        .replace("\\n", "\n")
        .replace("\\u0009", SEP)
        .replace("\\\\", "\\")

    private companion object {
        const val SEP = "\t"
        const val LIST = ""
    }
}
