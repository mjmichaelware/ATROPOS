/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.storage

import atropos.core.AtroposRepoRootLocator
import atropos.core.phase20.GlobalByteCeiling
import java.nio.file.Path
import java.time.Duration
import java.time.Instant

/**
 * The one thing callers ask "may I write this?".
 *
 * `SUP.STOR.FREE-SPACE-GATE`: "Wire as first check inside BoundedAgencyGate for
 * any FILE_MUTATION or WORKTREE action" — and the predicate,
 * `P(disk-full-crash)=0` under continuous operation.
 *
 * Three separate limits bind, and a caller that had to know about all three
 * would eventually check two. Composed here so a call site asks once:
 *
 * - the **device** will refuse a write when it is genuinely out of blocks
 *   ([FreeSpaceProbe]);
 * - the **declared ceiling** is the operator's promise about how much of the
 *   phone ATROPOS may occupy ([StorageConstitution]);
 * - the **bands** decide when a permitted write should still warn
 *   ([FreeSpaceGate]).
 *
 * Every refusal carries what could be freed, because §4.1 requires a failure to
 * state what to do about it — and on a phone "out of space" without a next step
 * is where an autonomous run stops for good.
 */
class StorageSupervisor(
    private val stateRoot: Path = AtroposRepoRootLocator.resolve().resolve(STATE_DIR),
    private val ceilingBytes: Long = DEFAULT_CEILING_BYTES,
    private val gate: FreeSpaceGate = FreeSpaceGate(),
    private val probe: FreeSpaceProbe = FreeSpaceProbe(stateRoot),
    private val accountant: StorageAccountant = StorageAccountant(stateRoot)
) {
    private val accountingLedger = StorageAccountingLedger()
    private val referenceGraph = ObjectReferenceGraph()
    private val leaseStore = ObjectLeaseStore()
    private val pinStore = ObjectPinStore()
    private val legalHoldStore = LegalHoldStore()
    private val tombstoneStore = TombstoneStore()
    private val markSweepPlanner = MarkSweepPlanner(referenceGraph, leaseStore, pinStore, legalHoldStore)
    private val garbageCollectionGate = GarbageCollectionGate()
    private val compactionPlanner = CompactionPlanner()
    private val tieringPolicy = TieringPolicy()
    private val watermarkGuard = LocalWatermarkGuard()
    private val remoteQuotaGuard = RemoteQuotaGuard(DEFAULT_CEILING_BYTES)
    private val costLedger = StorageCostLedger()
    private val deduplicationMetrics = DeduplicationMetricsCalculator()
    private val compressionManifest = CompressionManifest()
    private val orphanScanner = OrphanScanner(referenceGraph)
    private val checksumScrubber = ChecksumScrubber()
    private val deletionProofBuilder = DeletionProofBuilder()
    private val archiveRestoreVerifier = ArchiveRestoreVerifier(checksumScrubber)
    private val replicaHealthService = ReplicaHealthService()
    private val reconciliationService = StorageReconciliationService()
    private val projectBudgets = ProjectStorageBudgetStore()
    private val growthForecaster = StorageGrowthForecaster()
    private val inspector = StorageInspectorHOE()

    /** Existing storage boundary for durable-object governance consumers. */
    fun governanceStores(): StorageGovernanceStores = StorageGovernanceStores(
        accountingLedger,
        referenceGraph,
        leaseStore,
        pinStore,
        legalHoldStore,
        tombstoneStore
    )

    fun acquireLease(lease: ObjectLease, now: Instant): Boolean = leaseStore.acquire(lease, now)

    fun pinObject(pin: ObjectPin) { pinStore.pin(pin) }

    fun placeLegalHold(hold: LegalHold) { legalHoldStore.place(hold) }

    fun recordTombstone(tombstone: Tombstone) { tombstoneStore.record(tombstone) }

    fun planReclaim(objects: List<BlobObject>, now: Instant): List<MarkSweepCandidate> =
        markSweepPlanner.plan(objects, now)

    fun evaluateReclaim(bytes: Long): GarbageCollectionDecision =
        garbageCollectionGate.evaluate(constitution(), bytes)

    fun planCompaction(storageClass: String, objects: List<BlobObject>, maxBytes: Long): List<CompactionPlan> =
        compactionPlanner.plan(storageClass, objects, maxBytes)

    fun tierFor(age: java.time.Duration, pinned: Boolean, held: Boolean): RetentionTier =
        tieringPolicy.tierFor(age, pinned, held)

    /**
     * Typed retention projection used by the operator surface.
     *
     * The declared tier is a floor, not a suggestion. Age can only move a class
     * further down the reclaim order — a class declared COLD stays cold however
     * fresh it is — because the declaration says what the data is for, and age
     * says only how long it has sat. Reading the tier from age alone reported
     * every class as HOT the moment it was written, which is the "hidden
     * accumulation" `SUP.STOR.RETENTION-TIERS` exists to prevent.
     *
     * A referenced class is the exception: something is using it now, so it is
     * HOT regardless of what it was declared as.
     */
    fun retentionClass(storageClass: StorageClass, age: Duration = Duration.ZERO, referenced: Boolean = false): RetentionClass {
        val byAge = tierFor(age, pinned = false, held = referenced)
        val tier = if (referenced) byAge else maxOf(storageClass.tier, byAge)
        return RetentionClass(storageClass.id, tier, age)
    }

    fun evaluateWatermark(): WatermarkDecision {
        val current = constitution()
        return watermarkGuard.evaluate(current.usedBytes, current.ceilingBytes)
    }

    fun remoteQuota(): RemoteQuotaGuard = remoteQuotaGuard

    fun recordStorageCost(entry: StorageCostEntry) { costLedger.record(entry) }

    fun deduplicationMetrics(objects: Iterable<Pair<String, Long>>): DeduplicationMetrics =
        deduplicationMetrics.calculate(objects)

    fun recordCompression(record: CompressionRecord) { compressionManifest.record(record) }

    fun orphanObjects(objectIds: Iterable<String>): List<String> = orphanScanner.scan(objectIds)

    fun checksum(bytes: ByteArray): String = checksumScrubber.sha256(bytes)

    fun deletionProof(objectId: String, bytes: Long, reason: String, references: Int, protected: Boolean): DeletionProof =
        deletionProofBuilder.build(objectId, bytes, reason, references, protected)

    fun verifyArchive(bytes: ByteArray, expectedSha256: String): Boolean =
        archiveRestoreVerifier.verify(bytes, expectedSha256)

    fun replicaHealth(replicaId: String, expected: Map<String, String>, actual: Map<String, String>): ReplicaHealth =
        replicaHealthService.assess(replicaId, expected, actual)

    fun reconcileStorage(ledgerIds: Set<String>, diskIds: Set<String>): StorageReconciliation =
        reconciliationService.reconcile(ledgerIds, diskIds)

    fun putProjectBudget(budget: ProjectStorageBudget) { projectBudgets.put(budget) }

    fun projectBudget(projectId: String): ProjectStorageBudget? = projectBudgets.find(projectId)

    fun forecastStorage(samples: List<Long>): StorageGrowthForecast =
        growthForecaster.forecast(samples, ceilingBytes)

    fun renderStorageStatus(): String = inspector.render(constitution(), evaluateWatermark())

    /** The current picture, for `/storage status` and for the gate. */
    fun constitution(): StorageConstitution = accountant.measure(ceilingBytes)

    fun pressure(): Double = constitution().fractionUsed

    /**
     * Whether a write of [bytes] may proceed.
     *
     * The device is checked first. A ceiling with headroom left is irrelevant
     * when the filesystem itself will reject the write, and reporting the
     * ceiling's opinion in that case would send the operator to free ATROPOS
     * data when the problem is somewhere else on the device entirely.
     */
    fun admit(bytes: Long): FreeSpaceDecision {
        val constitution = constitution()

        if (bytes > GlobalByteCeiling.MAX_WORKSPACE_CEILING) {
            return FreeSpaceDecision.Refused(
                reason = "requested write exceeds the global byte ceiling of " +
                    "${GlobalByteCeiling.MAX_WORKSPACE_CEILING}B",
                reclaimableBytes = constitution.reclaimableBytes()
            )
        }

        if (probe.wouldExhaustDevice(bytes)) {
            val usable = probe.usableBytes()
            return FreeSpaceDecision.Refused(
                reason = if (usable == null) {
                    "the filesystem holding $stateRoot could not be read, so free space is unknown"
                } else {
                    "the device has ${usable}B usable and this write needs ${bytes}B " +
                        "plus ${FreeSpaceProbe.DEFAULT_RESERVE_BYTES}B of reserve"
                },
                reclaimableBytes = constitution.reclaimableBytes(),
                emergency = true
            )
        }

        return gate.evaluate(constitution, bytes)
    }

    /** What a refusal suggests freeing, largest first. */
    fun reclaimTargets(): List<StorageClass> = constitution().reclaimable()

    companion object {
        const val STATE_DIR: String = ".atropos"

        /**
         * `SUP.STOR.GLOBAL-BYTE-CEILING` sets "1–4 GB usable after system" as
         * the phone profile. 2 GiB is the middle of that band: enough for days
         * of autonomous work with evidence retained, and small enough that a
         * 64 GB phone with photos on it stays usable.
         *
         * The operator may raise it. The atom asks for that to be an explicit
         * confirmation, which is the caller's job — this is only the default.
         */
        const val DEFAULT_CEILING_BYTES: Long = 2L * 1024 * 1024 * 1024
    }
}
