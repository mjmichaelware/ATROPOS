/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.phase20

import kotlin.test.*

class Phase20LoopTest {

    @Test
    fun `legacy facade never fabricates evidence or verified mutations`() {
        val proposal = Phase20Loop.formulateProposal("MemoryStore", "Optimize retrieval")
        assertTrue(proposal.evidence.isEmpty())

        val decision = Phase20Loop.auditProposal(proposal)
        assertTrue(decision is AuditDecision.Rejected)

        val amendment = Phase20Loop.createAmendment(proposal, "+ optimizedLine()", 1)
        assertFalse(amendment.verified)

        val executed = Phase20Loop.executeAmendment(amendment)
        assertFalse(executed.verified)
    }

    @Test
    fun `canonical factory returns the existing self improvement owner`() {
        val loop = Phase20Loop.canonical(GovernanceLedger())
        assertTrue(loop is SelfImprovementLoop)
    }

    @Test
    fun `test auditor rejects invalid proposal`() {
        val emptyProposal = Phase20Proposal("ID-1", "Target", emptyList(), "")
        val decision = Phase20Loop.auditProposal(emptyProposal)
        assertTrue(decision is AuditDecision.Rejected)
    }
}
