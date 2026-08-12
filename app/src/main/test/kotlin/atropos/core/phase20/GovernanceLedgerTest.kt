/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.phase20

import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class GovernanceLedgerTest {

    private val start: Instant = Instant.parse("2026-01-01T00:00:00Z")
    private var now: Instant = start

    private fun ledger(root: Path = Files.createTempDirectory("gov")) =
        GovernanceLedger(repoRoot = root, clock = { now })

    private fun completeProposal(
        ledger: GovernanceLedger,
        proposedBy: String = "planner",
        territory: List<String> = listOf("src/main/kotlin/atropos/cli")
    ) = ledger.propose(
        proposedBy = proposedBy,
        summary = "Reduce redundant provider probes",
        necessity = listOf("sha256:evidence-1"),
        baseline = "3 probes per turn",
        target = "1 probe per turn",
        guardrails = listOf("no change to provider selection"),
        territory = territory,
        risk = "a stale health reading for one turn",
        rollback = "revert the commit; no durable state changes",
        metric = MetricDeclaration("probes_per_turn", 3.0, 1.0, lowerIsBetter = true)
    )

    @Test
    fun `a proposal survives being written and read back`() {
        val root = Files.createTempDirectory("gov")
        val written = completeProposal(ledger(root))

        val read = ledger(root).proposals().single()
        assertEquals(written.id, read.id)
        assertEquals("planner", read.proposedBy)
        assertEquals(listOf("sha256:evidence-1"), read.necessity)
        assertEquals("revert the commit; no durable state changes", read.rollback)
        assertTrue(read.isComplete())
    }

    @Test
    fun `an incomplete proposal is recorded, not discarded`() {
        // P20-G08 treats repeated under-specified proposals as a governance
        // deficiency; discarding them here would erase the evidence of it.
        val root = Files.createTempDirectory("gov")
        val store = ledger(root)
        store.propose(
            proposedBy = "planner",
            summary = "tidy things",
            necessity = emptyList(),
            baseline = "",
            target = "",
            guardrails = emptyList(),
            territory = listOf("src"),
            risk = "",
            rollback = "",
            metric = MetricDeclaration("", 0.0, 0.0, lowerIsBetter = true)
        )

        val read = ledger(root).proposals().single()
        assertFalse(read.isComplete())
        assertTrue(read.missingFields().contains("rollback"))
    }

    @Test
    fun `accepting writes an amendment and opens the observation period`() {
        val root = Files.createTempDirectory("gov")
        val store = ledger(root)
        val proposal = completeProposal(store)

        val outcome = store.accept(
            proposalId = proposal.id,
            acceptedBy = "operator",
            supersedes = "sha256:authority-v1",
            evidenceHashes = listOf("sha256:test-run")
        )

        assertTrue(outcome is LedgerOutcome.Accepted)
        val reread = ledger(root)
        assertEquals(1, reread.amendments().size)
        assertEquals(ProposalState.ACCEPTED, reread.proposals().single().state)
        assertEquals(1, reread.openObservationPeriods(now).size)
    }

    @Test
    fun `the amendment hash is independent of any source document`() {
        val store = ledger()
        val proposal = completeProposal(store)
        val outcome = store.accept(proposal.id, "operator", "sha256:authority-v1", listOf("sha256:e"))

        val amendment = (outcome as LedgerOutcome.Accepted).amendment
        assertEquals(64, amendment.sha256.length)
        // §20.1: the superseded hash is referenced, never replaced.
        assertEquals("sha256:authority-v1", amendment.supersedes)
        assertTrue(amendment.sha256 != amendment.supersedes)
    }

    @Test
    fun `the same proposal accepted by two people hashes differently`() {
        val a = ledger()
        val proposalA = completeProposal(a)
        val hashA = (a.accept(proposalA.id, "alice", "sha256:v1", listOf("sha256:e"))
            as LedgerOutcome.Accepted).amendment.sha256

        val b = ledger()
        val proposalB = completeProposal(b)
        val hashB = (b.accept(proposalB.id, "bob", "sha256:v1", listOf("sha256:e"))
            as LedgerOutcome.Accepted).amendment.sha256

        assertTrue(hashA != hashB, "the approver is part of what the amendment attests")
    }

    @Test
    fun `self-approval is refused and writes nothing`() {
        val root = Files.createTempDirectory("gov")
        val store = ledger(root)
        val proposal = completeProposal(store, proposedBy = "planner")

        val outcome = store.accept(proposal.id, "planner", "sha256:v1", listOf("sha256:e"))

        val refusal = assertNotNull(outcome as? LedgerOutcome.Refused)
        assertEquals("20.7", refusal.law)
        // A refused acceptance that left an amendment behind would be authority
        // nobody approved.
        assertTrue(ledger(root).amendments().isEmpty())
        assertTrue(ledger(root).openObservationPeriods(now).isEmpty())
    }

    @Test
    fun `an amendment with no cited evidence is refused`() {
        val store = ledger()
        val proposal = completeProposal(store)

        val refusal = assertNotNull(
            store.accept(proposal.id, "operator", "sha256:v1", emptyList()) as? LedgerOutcome.Refused
        )
        assertEquals("20.19", refusal.law)
    }

    @Test
    fun `an amendment that names no superseded authority is refused`() {
        val store = ledger()
        val proposal = completeProposal(store)

        val refusal = assertNotNull(
            store.accept(proposal.id, "operator", "  ", listOf("sha256:e")) as? LedgerOutcome.Refused
        )
        assertEquals("20.8", refusal.law)
    }

    @Test
    fun `a subsystem inside its observation period cannot change again`() {
        val root = Files.createTempDirectory("gov")
        val store = ledger(root)
        val first = completeProposal(store)
        store.accept(first.id, "operator", "sha256:v1", listOf("sha256:e"))

        now = start.plusSeconds(60)
        val second = ledger(root).let { it to completeProposal(it) }
        val refusal = assertNotNull(
            second.first.accept(second.second.id, "operator", "sha256:v2", listOf("sha256:e2"))
                as? LedgerOutcome.Refused
        )
        assertEquals("20.14", refusal.law)
    }

    @Test
    fun `the observation period expires and the subsystem reopens`() {
        val root = Files.createTempDirectory("gov")
        val store = ledger(root)
        val first = completeProposal(store)
        store.accept(first.id, "operator", "sha256:v1", listOf("sha256:e"))

        now = start.plusSeconds(GovernanceLedger.DEFAULT_OBSERVATION_SECONDS + 1)
        val reopened = ledger(root)
        assertTrue(reopened.openObservationPeriods(now).isEmpty())

        val second = completeProposal(reopened)
        assertTrue(
            reopened.accept(second.id, "operator", "sha256:v2", listOf("sha256:e2"))
                is LedgerOutcome.Accepted
        )
    }

    @Test
    fun `repeated failures quarantine the proposal`() {
        val root = Files.createTempDirectory("gov")
        val store = ledger(root)
        val proposal = completeProposal(store)

        repeat(GovernanceLedger.QUARANTINE_AFTER_FAILURES) { store.recordFailure(proposal.id) }

        val read = ledger(root).proposals().single()
        assertEquals(ProposalState.QUARANTINED, read.state)
        assertEquals(GovernanceLedger.QUARANTINE_AFTER_FAILURES, read.failureCount)

        val refusal = assertNotNull(
            ledger(root).accept(proposal.id, "operator", "sha256:v1", listOf("sha256:e"))
                as? LedgerOutcome.Refused
        )
        assertEquals("20.16", refusal.law)
    }

    @Test
    fun `the ledger is append-only — history survives a state change`() {
        val root = Files.createTempDirectory("gov")
        val store = ledger(root)
        val proposal = completeProposal(store)
        store.recordFailure(proposal.id)
        store.reject(proposal.id, "superseded by a simpler change")

        val raw = Files.readAllLines(root.resolve(".atropos/governance/ledger.log"))
        // Three proposal lines for one proposal: the original and both changes.
        assertEquals(3, raw.count { it.contains("kind=proposal") })
        // And the read collapses them to the latest state.
        assertEquals(ProposalState.REJECTED, ledger(root).proposals().single().state)
    }

    @Test
    fun `a rejection must state why`() {
        val store = ledger()
        val proposal = completeProposal(store)

        val refusal = assertNotNull(store.reject(proposal.id, "   ") as? LedgerOutcome.Refused)
        assertEquals("20.20", refusal.law)
    }

    @Test
    fun `an unknown proposal cannot be accepted`() {
        val refusal = assertNotNull(
            ledger().accept("prop-nope", "operator", "sha256:v1", listOf("sha256:e"))
                as? LedgerOutcome.Refused
        )
        assertTrue(refusal.reason.contains("prop-nope"))
    }

    @Test
    fun `proposing twice with the same id does not duplicate`() {
        val root = Files.createTempDirectory("gov")
        val store = ledger(root)
        val proposal = completeProposal(store)
        store.propose(proposal)

        assertEquals(1, ledger(root).proposals().size)
    }

    @Test
    fun `counts leave unobserved metrics unmeasured rather than zero`() {
        val root = Files.createTempDirectory("gov")
        val store = ledger(root)
        val proposal = completeProposal(store)
        store.accept(proposal.id, "operator", "sha256:v1", listOf("sha256:e"))

        val metrics = GovernanceMetrics(ledger(root).counts(now))
        // The ledger observes observation periods and nothing else. Reporting a
        // false-VERIFIED rate it cannot measure would be inventing a number.
        assertEquals(null, metrics.falseVerifiedRate)
        assertEquals(null, metrics.territoryViolationRate)
        assertTrue(metrics.unmeasured().contains("falseVerifiedRate"))
        assertEquals(0.0, metrics.observationSuccess)
    }

    @Test
    fun `an empty ledger reads as empty rather than failing`() {
        val store = ledger()
        assertTrue(store.proposals().isEmpty())
        assertTrue(store.amendments().isEmpty())
        assertTrue(store.observationPeriods().isEmpty())
    }
}
