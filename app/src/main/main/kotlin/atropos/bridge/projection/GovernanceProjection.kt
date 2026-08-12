/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.bridge.projection

import atropos.bridge.http.JsonWriter
import atropos.core.phase20.GovernanceMetrics
import atropos.core.phase20.ImprovementProposal
import atropos.core.phase20.AuthorityAmendment
import atropos.core.phase20.ObservationPeriod
import atropos.core.security.RedactionFilter
import java.time.Instant

/**
 * Projects the Phase 20 governance state onto the wire.
 *
 * `C4-IF-01..05` are all presentation atoms over machinery the engine owns, and
 * §20.20 fixes what they must be able to show: "the system must explain why,
 * where, how it changed and why the result is better".
 *
 * The rule this file follows throughout is that an unmeasured value is emitted
 * as `null`, never as a zero or a default. `P20-S04`'s metrics are the obvious
 * case — a 0% false-VERIFIED rate computed from no claims is the most
 * flattering possible lie — but the same applies to a proposal with no metric
 * and an amendment with no evidence: the surface has to be able to say "this
 * was never measured" in a way a client cannot mistake for "this measured well".
 */
class GovernanceProjection(
    private val redactionFilter: RedactionFilter = RedactionFilter()
) {
    fun renderProposals(
        proposals: List<ImprovementProposal>,
        openPeriods: List<ObservationPeriod>,
        now: Instant
    ): String = JsonWriter.obj(
        "ok" to JsonWriter.bool(true),
        "proposals" to JsonWriter.arr(proposals.map(::proposal)),
        // C4-IF-05: a subsystem inside its observation period cannot change
        // again yet, and the operator sees the timer rather than a silent
        // refusal later.
        "cooldowns" to JsonWriter.arr(
            openPeriods.filter { it.isOpenAt(now) }.map { period ->
                JsonWriter.obj(
                    "subsystem" to JsonWriter.str(period.subsystem),
                    "remainingSeconds" to JsonWriter.num(period.remainingSecondsAt(now))
                )
            }
        )
    )

    private fun proposal(proposal: ImprovementProposal): String = JsonWriter.obj(
        "id" to JsonWriter.str(proposal.id),
        "proposedBy" to JsonWriter.str(redact(proposal.proposedBy)),
        "summary" to JsonWriter.str(redact(proposal.summary)),
        "state" to JsonWriter.str(proposal.state.name.lowercase()),
        // C4-IF-04: the metric has to be visible *before* the change, so a
        // reviewer can see it was declared rather than chosen afterwards.
        "metric" to JsonWriter.obj(
            "name" to JsonWriter.str(proposal.metric.name),
            "baseline" to JsonWriter.num(proposal.metric.baselineValue),
            "target" to JsonWriter.num(proposal.metric.targetValue),
            "lowerIsBetter" to JsonWriter.bool(proposal.metric.lowerIsBetter),
            "declared" to JsonWriter.bool(proposal.metric.isDeclared())
        ),
        "baseline" to JsonWriter.str(redact(proposal.baseline)),
        "target" to JsonWriter.str(redact(proposal.target)),
        "guardrails" to JsonWriter.strArr(proposal.guardrails.map(::redact)),
        "territory" to JsonWriter.strArr(proposal.territory.map(::redact)),
        "risk" to JsonWriter.str(redact(proposal.risk)),
        "rollback" to JsonWriter.str(redact(proposal.rollback)),
        "necessityHashes" to JsonWriter.strArr(proposal.necessity),
        "complete" to JsonWriter.bool(proposal.isComplete()),
        // Named rather than counted: an operator fixing a proposal needs to
        // know which declaration is missing.
        "missing" to JsonWriter.strArr(proposal.missingFields()),
        "failureCount" to JsonWriter.num(proposal.failureCount)
    )

    /** C4-IF-03: the amendment registry, with the superseded hash left intact. */
    fun renderAmendments(amendments: List<AuthorityAmendment>): String = JsonWriter.obj(
        "ok" to JsonWriter.bool(true),
        "amendments" to JsonWriter.arr(
            amendments.map { amendment ->
                JsonWriter.obj(
                    "id" to JsonWriter.str(amendment.id),
                    "proposalId" to JsonWriter.str(amendment.proposalId),
                    "sha256" to JsonWriter.str(amendment.sha256),
                    "supersedes" to JsonWriter.str(amendment.supersedes),
                    "acceptedBy" to JsonWriter.str(redact(amendment.acceptedBy)),
                    "acceptedAt" to JsonWriter.str(amendment.acceptedAt.toString()),
                    "evidenceHashes" to JsonWriter.strArr(amendment.evidenceHashes)
                )
            }
        )
    )

    /** P20-S04. Null crosses the wire as null so a client cannot read it as zero. */
    fun renderMetrics(metrics: GovernanceMetrics): String = JsonWriter.obj(
        "ok" to JsonWriter.bool(true),
        "healthy" to JsonWriter.bool(metrics.healthy()),
        "falseVerifiedRate" to nullableNum(metrics.falseVerifiedRate),
        "territoryViolationRate" to nullableNum(metrics.territoryViolationRate),
        "recoveryCompleteness" to nullableNum(metrics.recoveryCompleteness),
        "observationSuccess" to nullableNum(metrics.observationSuccess),
        "tokensPerVerifiedChange" to nullableNum(metrics.tokensPerVerifiedChange),
        "unmeasured" to JsonWriter.strArr(metrics.unmeasured())
    )

    private fun nullableNum(value: Double?): String =
        value?.let(JsonWriter::num) ?: "null"

    private fun redact(value: String): String = redactionFilter.redact(value)
}
