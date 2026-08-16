/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.storage

import atropos.core.phase20.GlobalByteCeiling
import java.io.ByteArrayInputStream
import java.nio.file.Files
import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StorageGovernanceAtomsTest {

    @Test
    fun `storage admission refuses a write above the global byte ceiling`() {
        val supervisor = StorageSupervisor(Files.createTempDirectory("atropos-byte-ceiling-"))
        val decision = supervisor.admit(GlobalByteCeiling.MAX_WORKSPACE_CEILING + 1)
        assertTrue(decision is FreeSpaceDecision.Refused)
        assertTrue((decision as FreeSpaceDecision.Refused).reason.contains("global byte ceiling"))
    }

    @Test
    fun `the supervisor gate covers negative global and ordinary admission paths`() {
        val root = Files.createTempDirectory("atropos-admission-contract-")
        val supervisor = StorageSupervisor(stateRoot = root, ceilingBytes = 10_000)

        assertTrue(supervisor.admit(-1) is FreeSpaceDecision.Refused)
        assertTrue(supervisor.admit(GlobalByteCeiling.MAX_WORKSPACE_CEILING + 1) is FreeSpaceDecision.Refused)
        assertTrue(supervisor.admit(1).permitted)
    }
    private val now = Instant.parse("2026-01-01T00:00:00Z")
    private fun blob(id: String, bytes: Long = 10) = BlobObject(id, bytes, now.minus(Duration.ofDays(10)), "default") {
        ByteArrayInputStream(ByteArray(0))
    }

    @Test
    fun `accounting and references retain exact ownership`() {
        val ledger = StorageAccountingLedger()
        ledger.record(StorageAccountingEntry("a", 4, "evidence", now))
        assertEquals(4, ledger.totalBytes())
        val graph = ObjectReferenceGraph()
        graph.reference("run", "a")
        assertTrue(graph.isReferenced("a"))
        assertEquals(listOf("run"), graph.referencedBy("a"))
    }

    @Test
    fun `leases pins holds and tombstones protect object identity`() {
        val leases = ObjectLeaseStore()
        assertTrue(leases.acquire(ObjectLease("a", "run", now.plusSeconds(10)), now))
        assertFalse(leases.acquire(ObjectLease("a", "other", now.plusSeconds(10)), now))
        val pins = ObjectPinStore().also { it.pin(ObjectPin("a", "operator review")) }
        val holds = LegalHoldStore().also { it.place(LegalHold("a", "owner", "audit")) }
        val tombstones = TombstoneStore().also { it.record(Tombstone("a", 1, "proof")) }
        assertTrue(pins.isPinned("a"))
        assertTrue(holds.isHeld("a"))
        assertTrue(tombstones.contains("a"))
    }

    @Test
    fun `planning refuses protected objects and bounds compaction`() {
        val refs = ObjectReferenceGraph().also { it.reference("run", "held") }
        val leases = ObjectLeaseStore()
        val pins = ObjectPinStore().also { it.pin(ObjectPin("pinned", "operator")) }
        val holds = LegalHoldStore()
        val planner = MarkSweepPlanner(refs, leases, pins, holds)
        val candidates = planner.plan(listOf(blob("held"), blob("pinned"), blob("free")), now)
        assertEquals(listOf("free"), candidates.map { it.objectId })
        assertEquals(2, CompactionPlanner().plan("evidence", listOf(blob("a", 4), blob("b", 4)), 5).size)
    }

    @Test
    fun `storage guards and integrity services are deterministic`() {
        val constitution = StorageConstitution(100, listOf(StorageClass("warm", RetentionTier.WARM, 20)))
        assertTrue(GarbageCollectionGate().evaluate(constitution, 20).allowed)
        assertFalse(LocalWatermarkGuard().evaluate(99, 100).allowed)
        assertTrue(RemoteQuotaGuard(100).admit(40, 60))
        assertEquals(RetentionTier.COLD, TieringPolicy().tierFor(Duration.ofDays(8), false, false))
        val bytes = "payload".toByteArray()
        val checksum = ChecksumScrubber().sha256(bytes)
        assertTrue(ArchiveRestoreVerifier().verify(bytes, checksum))
        assertFalse(ArchiveRestoreVerifier().verify("other".toByteArray(), checksum))
    }

    @Test
    fun `cost compression orphan replica and reconciliation evidence is retained`() {
        val costs = StorageCostLedger().also { it.record(StorageCostEntry("cas", 12, now)) }
        assertEquals(12, costs.bytesFor("cas"))
        val metrics: DeduplicationMetrics = DeduplicationMetricsCalculator().calculate(listOf("a" to 4, "a" to 4))
        assertEquals(1, metrics.uniqueObjects)
        val compression = CompressionManifest().also { it.record(CompressionRecord("a", 10, 5, "zstd")) }
        assertEquals(5, compression.find("a")?.storedBytes)
        val retention: RetentionClass = RetentionClass("evidence", RetentionTier.COLD, Duration.ZERO)
        assertTrue(retention.eligible(Duration.ofDays(1), referenced = false))
        val graph = ObjectReferenceGraph().also { it.reference("run", "live") }
        assertEquals(listOf("dead"), OrphanScanner(graph).scan(listOf("live", "dead")))
        val health = ReplicaHealthService().assess("r1", mapOf("a" to "h"), mapOf("a" to "h"))
        assertTrue(health.healthy)
        val reconciliation = StorageReconciliationService().reconcile(setOf("a"), setOf("a", "b"))
        assertFalse(reconciliation.balanced)
        assertEquals(listOf("b"), reconciliation.missingFromLedger)
        val proof: DeletionProof = DeletionProofBuilder().build("a", 1, "expired", 0, false)
        assertTrue(proof.allowed)
        assertFalse(DeletionProofBuilder().build("a", 1, "held", 1, true).allowed)
        val budget = ProjectStorageBudget("p", 100, 40)
        assertTrue(budget.admits(60))
        val forecast = StorageGrowthForecaster().forecast(listOf(10, 20, 30), 100)
        assertEquals(10, forecast.averageBytesPerSample)
        assertTrue(StorageInspectorHOE().render(StorageConstitution(100), WatermarkDecision(true, false, "ok")).contains("storage used"))
    }

    @Test
    fun `supervisor exposes the typed retention projection`() {
        val supervisor = StorageSupervisor(java.nio.file.Files.createTempDirectory("atropos-retention-supervisor-"))
        val projection = supervisor.retentionClass(StorageClass("evidence", RetentionTier.COLD, 10))
        assertEquals("evidence", projection.id)
        assertEquals(RetentionTier.COLD, projection.tier)
    }
}
