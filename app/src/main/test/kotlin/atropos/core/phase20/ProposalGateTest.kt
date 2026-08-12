/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.phase20

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProposalGateTest {

    private val now: Instant = Instant.parse("2026-08-04T00:00:00Z")
    private val gate = ProposalGate()

    private fun proposal(
        territory: List<String> = listOf("core/provider"),
        proposedBy: String = "advisor",
        failures: Int = 0,
        state: ProposalState = ProposalState.OPEN,
        necessity: List<String> = listOf("evidence-hash-1"),
        rollback: String = "revert the commit"
    ) = ImprovementProposal(
        id = "prop-1",
        proposedBy = proposedBy,
        summary = "reduce false VERIFIED",
        necessity = necessity,
        baseline = "false-verified rate 3%",
        target = "below 1%",
        guardrails = listOf("no weakening of the completion gate"),
        territory = territory,
        risk = "low",
        rollback = rollback,
        metric = MetricDeclaration("false_verified_rate", 3.0, 1.0, lowerIsBetter = true),
        createdAt = now,
        state = state,
        failureCount = failures
    )

    private fun decide(
        p: ImprovementProposal = proposal(),
        approver: String = "auditor",
        periods: List<ObservationPeriod> = emptyList(),
        human: Boolean = false
    ) = gate.evaluate(p, approver, periods, now, human)

    @Test
    fun `a complete proposal with an independent approver is accepted`() {
        assertTrue(decide().accepted)
    }

    @Test
    fun `20_7 — a component cannot approve its own proposal`() {
        val decision = decide(approver = "advisor") as ProposalDecision.Refused
        assertEquals("20.7", decision.law)
        assertTrue(decision.reason.contains("cannot approve its own"))
    }

    @Test
    fun `20_7 — an unattributed acceptance is refused`() {
        assertEquals("20.7", (decide(approver = "  ") as ProposalDecision.Refused).law)
    }

    @Test
    fun `20_6 — every missing declaration is named`() {
        val incomplete = proposal(necessity = emptyList(), rollback = "  ")
        val decision = decide(incomplete) as ProposalDecision.Refused
        assertEquals("20.6", decision.law)
        assertTrue(decision.reason.contains("necessity"))
        assertTrue(decision.reason.contains("rollback"))
    }

    @Test
    fun `20_16 — repeated failures quarantine the proposal`() {
        assertEquals("20.16", (decide(proposal(failures = 3)) as ProposalDecision.Refused).law)
        assertEquals(
            "20.16",
            (decide(proposal(state = ProposalState.QUARANTINED)) as ProposalDecision.Refused).law
        )
    }

    @Test
    fun `20_14 — a subsystem inside its observation period is refused`() {
        val periods = listOf(ObservationPeriod("core/provider", now.minusSeconds(10), 600))
        val decision = decide(periods = periods) as ProposalDecision.Refused
        assertEquals("20.14", decision.law)
        assertTrue(decision.reason.contains("observation period"))
    }

    @Test
    fun `an expired observation period no longer blocks`() {
        val periods = listOf(ObservationPeriod("core/provider", now.minusSeconds(1_000), 600))
        assertTrue(decide(periods = periods).accepted)
    }

    @Test
    fun `20_12 — touching governance requires human authorisation`() {
        val meta = proposal(territory = listOf("core/verification/Gate.kt"))
        assertEquals("20.12", (decide(meta) as ProposalDecision.Refused).law)
        assertTrue(decide(meta, human = true).accepted, "an authorised human may still proceed")
    }

    @Test
    fun `every refusal cites the law that produced it`() {
        listOf(
            decide(approver = "advisor"),
            decide(proposal(necessity = emptyList())),
            decide(proposal(failures = 9))
        ).forEach { decision ->
            val refused = decision as ProposalDecision.Refused
            assertTrue(refused.law.startsWith("20."), "a refusal must be traceable to authority")
        }
    }

    @Test
    fun `20_13 — improvement is measured against the predeclared baseline`() {
        val metric = MetricDeclaration("rate", 3.0, 1.0, lowerIsBetter = true)
        assertTrue(metric.improvedBy(2.0))
        assertFalse(metric.improvedBy(3.5), "moving away from the target is not an improvement")
        assertFalse(metric.improvedBy(3.0), "unchanged is not improved")
    }

    @Test
    fun `a metric whose baseline equals its target is not a declaration`() {
        assertFalse(MetricDeclaration("rate", 1.0, 1.0, true).isDeclared())
    }

    @Test
    fun `20_8 — an amendment carries its own hash and leaves the original intact`() {
        val amendment = AuthorityAmendment(
            id = "amd-1",
            proposalId = "prop-1",
            sha256 = "amendment-hash",
            supersedes = "original-hash",
            acceptedBy = "auditor",
            acceptedAt = now,
            evidenceHashes = listOf("e1", "e2")
        )
        assertTrue(amendment.sha256 != amendment.supersedes)
        assertTrue(amendment.render().contains("supersedes=original-hash"))
    }
}
