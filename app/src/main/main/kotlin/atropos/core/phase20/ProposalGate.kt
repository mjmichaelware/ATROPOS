/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.phase20

import java.time.Instant

/**
 * The gate an improvement proposal must pass before it becomes authority.
 *
 * This is where the Phase 20 laws stop being prose. Each refusal below is a
 * named law, and each exists because the failure it prevents is one the system
 * cannot detect afterwards:
 *
 * §20.6 — a proposal missing any of the six declarations is refused. An
 * incomplete proposal that reached acceptance would become authority nobody
 * could evaluate, because the fields that make evaluation possible are the ones
 * it lacks.
 *
 * §20.7 — proposer may not approve itself. Without this the loop closes on
 * itself and "independent verification" means a component agreeing with what it
 * just said.
 *
 * §20.16 — repeated failures quarantine. A proposal that keeps failing is not
 * unlucky; retrying it forever burns budget and hides the real deficiency.
 *
 * §20.14 / `P20-H04` — a subsystem inside its observation period is refused.
 * Oscillating changes to one subsystem look like progress and are the pattern
 * `P20-G08` names as a governance deficiency.
 *
 * §20.12 / `P20-H02` — a proposal touching an immutable invariant needs human
 * authorisation, never an automated approver.
 */
class ProposalGate(
    private val quarantineAfterFailures: Int = 3,
    private val meta: Set<String> = META_LEVEL_KEYS
) {
    fun evaluate(
        proposal: ImprovementProposal,
        approver: String,
        openPeriods: List<ObservationPeriod>,
        now: Instant,
        humanAuthorised: Boolean = false
    ): ProposalDecision {
        if (proposal.state == ProposalState.QUARANTINED) {
            return refuse("20.16", "proposal ${proposal.id} is quarantined and needs new evidence to reopen")
        }
        if (proposal.failureCount >= quarantineAfterFailures) {
            return refuse("20.16", "proposal ${proposal.id} has failed ${proposal.failureCount} times and is quarantined")
        }
        if (!proposal.isComplete()) {
            return refuse("20.6", "proposal is missing required declarations: ${proposal.missingFields().joinToString(", ")}")
        }
        if (approver.isBlank()) {
            return refuse("20.7", "an acceptance must name its approver")
        }
        if (approver == proposal.proposedBy) {
            return refuse("20.7", "${proposal.proposedBy} cannot approve its own proposal")
        }
        val touchedMeta = proposal.territory.filter { path -> meta.any { path.contains(it) } }
        if (touchedMeta.isNotEmpty() && !humanAuthorised) {
            return refuse(
                "20.12",
                "proposal touches immutable governance (${touchedMeta.joinToString(", ")}) and requires human authorisation"
            )
        }
        val blocking = openPeriods.firstOrNull { period ->
            period.isOpenAt(now) && proposal.territory.any { it.startsWith(period.subsystem) }
        }
        if (blocking != null) {
            return refuse(
                "20.14",
                "${blocking.subsystem} is inside its observation period for another " +
                    "${blocking.remainingSecondsAt(now)}s"
            )
        }
        return ProposalDecision.Accepted(proposal.id, approver)
    }

    private fun refuse(law: String, reason: String) = ProposalDecision.Refused(law, reason)

    companion object {
        /**
         * Paths whose change alters the rules rather than the system.
         *
         * `P20-NS05`: the system may not rewrite its own success predicates
         * without an external gate.
         */
        val META_LEVEL_KEYS: Set<String> = setOf(
            "core/verification",
            "core/policy",
            "core/territory",
            "core/phase20",
            "AGENTS.md"
        )
    }
}

sealed class ProposalDecision {
    data class Accepted(val proposalId: String, val approver: String) : ProposalDecision()
    data class Refused(
        /** The law that refused it, so the decision is traceable to authority. */
        val law: String,
        val reason: String
    ) : ProposalDecision()

    val accepted: Boolean get() = this is Accepted
}
