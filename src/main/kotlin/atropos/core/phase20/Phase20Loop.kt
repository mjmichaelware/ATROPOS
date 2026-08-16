/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.phase20

object Phase20Loop {
    /** Canonical loop factory used by governance; this object owns no loop. */
    fun canonical(ledger: GovernanceLedger): SelfImprovementLoop = SelfImprovementLoop(ledger)

    // P20-20.1: Gather evidence
    fun gatherEvidence(target: String): List<String> {
        // Legacy callers cannot fabricate evidence. Real observations enter
        // through Phase20GovernanceService and its detector/ledger chain.
        return emptyList()
    }

    // P20-20.2: Formulate Proposal
    fun formulateProposal(target: String, intent: String): Phase20Proposal {
        return Phase20Proposal(
            id = "PROP-${target.hashCode()}",
            target = target,
            evidence = gatherEvidence(target),
            intent = intent
        )
    }

    // P20-20.3 to 20.5: Auditor review
    fun auditProposal(proposal: Phase20Proposal): AuditDecision {
        if (proposal.evidence.isEmpty()) {
            return AuditDecision.Rejected("P20-20.4: Missing evidence")
        }
        if (proposal.intent.isBlank()) {
            return AuditDecision.Rejected("P20-20.5: Missing intent")
        }
        return AuditDecision.Approved
    }

    // P20-20.6 to 20.8: Versioned Amendment creation
    fun createAmendment(proposal: Phase20Proposal, diff: String, version: Int): VersionedAmendment {
        return VersionedAmendment(
            proposalId = proposal.id,
            version = version,
            diff = diff,
            verified = false
        )
    }

    // P20-20.9: Phase 11 execution trigger
    fun executeAmendment(amendment: VersionedAmendment): VersionedAmendment {
        // Phase 20 cannot execute source mutations. Phase 11 must return an
        // independent gate result before this contract can become verified.
        // Returning an unverified contract is intentionally fail-closed.
        return amendment.copy(verified = false)
    }
}
