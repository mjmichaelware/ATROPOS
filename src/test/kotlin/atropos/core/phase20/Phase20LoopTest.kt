/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.phase20

import kotlin.test.*

class Phase20LoopTest {

    @Test
    fun `test complete phase 20 loop`() {
        val proposal = Phase20Loop.formulateProposal("MemoryStore", "Optimize retrieval")
        assertEquals(1, proposal.evidence.size)

        val decision = Phase20Loop.auditProposal(proposal)
        assertEquals(AuditDecision.Approved, decision)

        val amendment = Phase20Loop.createAmendment(proposal, "+ optimizedLine()", 1)
        assertFalse(amendment.verified)

        val executed = Phase20Loop.executeAmendment(amendment)
        assertTrue(executed.verified)
    }

    @Test
    fun `test auditor rejects invalid proposal`() {
        val emptyProposal = Phase20Proposal("ID-1", "Target", emptyList(), "")
        val decision = Phase20Loop.auditProposal(emptyProposal)
        assertTrue(decision is AuditDecision.Rejected)
    }
}
