/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.approval

import java.nio.file.Files
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PendingApprovalStoreTest {

    private fun store(): PendingApprovalStore {
        var tick = 0L
        val base = Instant.parse("2026-08-04T00:00:00Z")
        return PendingApprovalStore(
            repoRoot = Files.createTempDirectory("approvals"),
            clock = { base.plusSeconds(tick++) }
        )
    }

    private fun record(s: PendingApprovalStore, proposal: String = "prop-1") =
        s.record(proposal, "patch:p-1", "FILE_MUTATION", listOf("src/main"), "writes outside auto-allow")

    @Test
    fun `a recorded approval is pending and carries what a human needs to decide`() {
        val s = store()
        val approval = record(s)

        assertTrue(approval.isPending)
        assertEquals("patch:p-1", approval.actor)
        assertEquals("FILE_MUTATION", approval.operation)
        assertEquals(listOf("src/main"), approval.territory)
        assertTrue(approval.reason.isNotBlank())
        assertEquals(1, s.pending().size)
    }

    @Test
    fun `recording the same proposal twice does not queue a second card`() {
        val s = store()
        val first = record(s)
        val second = record(s)

        assertEquals(first.id, second.id, "a retried proposal must not create a second pending card")
        assertEquals(1, s.pending().size)
    }

    @Test
    fun `a decision is durable and removes the item from pending`() {
        val s = store()
        val approval = record(s)

        val outcome = s.decide(approval.id, approved = true, decidedBy = "operator", surface = ApprovalSurface.BRIDGE)

        assertTrue(outcome is ApprovalOutcome.Recorded)
        assertTrue(s.pending().isEmpty())
        val stored = s.all().single { it.id == approval.id }
        assertFalse(stored.isPending)
        assertEquals("operator", stored.decision?.decidedBy)
        assertEquals(ApprovalSurface.BRIDGE, stored.decision?.surface)
    }

    @Test
    fun `an unattributed decision is refused`() {
        val s = store()
        val approval = record(s)

        val outcome = s.decide(approval.id, approved = true, decidedBy = "  ", surface = ApprovalSurface.BRIDGE)

        assertTrue(outcome is ApprovalOutcome.Refused)
        assertTrue((outcome as ApprovalOutcome.Refused).reason.contains("who made it"))
        assertEquals(1, s.pending().size, "a refused decision must leave the item pending")
    }

    @Test
    fun `a settled approval cannot be silently re-decided`() {
        val s = store()
        val approval = record(s)
        s.decide(approval.id, approved = false, decidedBy = "operator", surface = ApprovalSurface.CLI)

        val second = s.decide(approval.id, approved = true, decidedBy = "someone-else", surface = ApprovalSurface.BRIDGE)

        assertTrue(second is ApprovalOutcome.Refused)
        assertEquals(false, s.all().single { it.id == approval.id }.decision?.approved)
    }

    @Test
    fun `deciding an unknown id is refused rather than creating one`() {
        val s = store()

        val outcome = s.decide("apr-nope", approved = true, decidedBy = "operator", surface = ApprovalSurface.BRIDGE)

        assertTrue(outcome is ApprovalOutcome.Refused)
        assertTrue(s.all().isEmpty())
    }

    @Test
    fun `history survives a new store over the same root`() {
        val root = Files.createTempDirectory("approvals-restart")
        val first = PendingApprovalStore(repoRoot = root)
        val approval = first.record("prop-9", "patch:p-9", "SHELL", listOf("src"), "needs a human")
        first.decide(approval.id, approved = true, decidedBy = "operator", surface = ApprovalSurface.CLI)

        val reopened = PendingApprovalStore(repoRoot = root)

        assertTrue(reopened.pending().isEmpty())
        assertEquals("operator", reopened.all().single().decision?.decidedBy)
    }

    @Test
    fun `secret-bearing reason text is redacted before it is persisted`() {
        val root = Files.createTempDirectory("approvals-secret")
        val s = PendingApprovalStore(repoRoot = root)
        val secret = "sk-" + "D".repeat(24)

        s.record("prop-s", "patch:p", "SHELL", listOf("src"), "used $secret")

        val onDisk = Files.readString(root.resolve(".atropos/approvals/pending.log"))
        assertFalse(onDisk.contains(secret), "an approval record must not persist a raw secret")
    }

    @Test
    fun `a record with an empty territory declares none rather than all`() {
        val s = store()
        val approval = s.record("prop-t", "patch:p", "SHELL", emptyList(), "no territory declared")

        assertTrue(approval.territory.isEmpty())
    }
}
