/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.storage

import java.io.File
import java.time.Duration
import java.time.Instant

/**
 * Collection and integrity for the blob store — the `StorageDriver` side of
 * storage, as opposed to the worktree and evidence trees.
 *
 * [WorktreeGc] and [EvidenceBundleGc] walk directories they own. Blobs are
 * different: they arrive through a [StorageDriver], so nothing can enumerate
 * them without going through the driver, and until this existed nothing did.
 * The parts were all present — a driver, a policy enforcer, a sweeper, a quota
 * tracker, a metrics reporter — and no code path joined them, which meant the
 * blob store had a collector on paper and unbounded growth in practice.
 *
 * Retention comes from [RetentionPolicy] and nowhere else. The blob layer has
 * its own rule type ([StorageRetentionRule]), and the temptation is to declare
 * windows there too; that would be the second policy, and the second policy is
 * the one nobody remembers to update. [rulesFrom] projects the declared classes
 * into the shape [GcPolicyEnforcer] wants, so a window changes in one file.
 */
class BlobStoreGc(
    private val driver: StorageDriver,
    private val policy: RetentionPolicy = RetentionPolicy(),
    private val quota: StorageQuotaTracker = StorageQuotaTracker(DEFAULT_CEILING_BYTES),
    private val metrics: GcMetricsReporter = GcMetricsReporter(),
    private val clock: () -> Instant = Instant::now
) {
    private val enforcer = GcPolicyEnforcer(rulesFrom(policy))

    private val integrity = StorageIntegrityChecker(driver)

    /**
     * @param pressure how close storage is to its ceiling, 0.0 to 1.0. Above
     *   [RetentionPolicy.EMERGENCY_PRESSURE] the safe-deletion boundary is
     *   pulled back so that items which are merely cold become eligible.
     * @param dryRun true reports without deleting, and is the default at every
     *   automatic call site.
     */
    fun collect(pressure: Double = 0.0, dryRun: Boolean = true): GcOutcome {
        val startedAt = clock()
        val candidates = driver.listAllMetadata()
        val watermark = watermarkFor(pressure, startedAt)
        val eligibleIds = enforcer.filterEligible(candidates, watermark).toSet()

        val byId = candidates.associateBy { it.first }
        val removed = mutableListOf<GcCandidate>()
        val retained = mutableListOf<GcRetention>()
        val failed = mutableListOf<String>()

        candidates.forEach { (id, ruleId, createdAt) ->
            if (id !in eligibleIds) {
                retained += GcRetention(
                    id = id,
                    bytes = sizeOf(id),
                    heldBy = "$ruleId retention window (age ${ageOf(createdAt, startedAt).toDays()}d)"
                )
            }
        }

        var freed = 0L
        if (dryRun) {
            eligibleIds.forEach { id ->
                removed += candidateFor(id, byId[id]?.second.orEmpty(), byId[id]?.third ?: startedAt, startedAt)
            }
        } else {
            // The sweeper owns the delete/release pairing: a blob whose bytes
            // are freed on disk but never released from the quota tracker
            // leaves the ceiling permanently overstated, and after enough
            // passes the store refuses writes with the disk half empty.
            val sizes = eligibleIds.associateWith { sizeOf(it) }
            val before = eligibleIds.toSet()
            freed = GcSweeper(enforcer, quota) { id -> sizes[id] ?: 0L }
                .sweep(candidates.filter { it.first in before }, watermark)

            eligibleIds.forEach { id ->
                if (driver.read(id) == null) {
                    removed += candidateFor(id, byId[id]?.second.orEmpty(), byId[id]?.third ?: startedAt, startedAt)
                } else {
                    failed += id
                }
            }
        }

        metrics.recordPass(
            GcPassResult(
                watermarkId = watermark.watermarkId,
                timestamp = startedAt,
                objectsScanned = candidates.size,
                objectsDeleted = removed.size,
                bytesFreed = if (dryRun) removed.sumOf { it.bytes } else freed,
                durationMs = Duration.between(startedAt, clock()).toMillis()
            )
        )

        return GcOutcome(
            storageClass = STORAGE_CLASS,
            removed = removed.sortedBy { it.id },
            retained = retained.sortedBy { it.id },
            failed = failed.sorted(),
            dryRun = dryRun
        )
    }

    /**
     * Bit-rot and tamper detection over the content-addressed blobs.
     *
     * Only ids that are themselves a SHA-256 are checked, because only for
     * those is the expected digest knowable without a sidecar: the id *is* the
     * claim about the content. A blob stored under an opaque id has nothing to
     * compare against and is reported as unverifiable rather than as passing —
     * an integrity report that silently counts unchecked objects as sound is
     * worse than no report.
     */
    fun verifyIntegrity(): IntegrityReport {
        val ids = driver.listAllMetadata().map { it.first }
        val addressed = ids.filter { CONTENT_ADDRESS.matches(it) }
        val corrupt = addressed.filterNot { integrity.verifyChecksum(it, it) }
        return IntegrityReport(
            checked = addressed.sorted(),
            corrupt = corrupt.sorted(),
            unverifiable = (ids - addressed.toSet()).sorted()
        )
    }

    /** Every pass this instance has run, for `/storage gc history`. */
    fun history(): List<GcPassResult> = metrics.getHistory()

    fun totalBytesFreed(): Long = metrics.getTotalBytesFreed()

    private fun watermarkFor(pressure: Double, now: Instant): GcWatermark {
        // Under pressure the boundary moves *forward* in time, so that objects
        // whose expiry has not yet passed become eligible. It never passes the
        // watermark timestamp itself, which GcWatermark rejects.
        val boundary =
            if (pressure >= RetentionPolicy.EMERGENCY_PRESSURE) now
            else now.minus(EMERGENCY_MARGIN)
        return GcWatermark(
            watermarkId = "blob-${now.toEpochMilli()}",
            timestamp = now,
            safeDeletionBoundary = boundary,
            enforcedBytes = quota.getUsage()
        )
    }

    private fun candidateFor(id: String, ruleId: String, createdAt: Instant, now: Instant): GcCandidate =
        GcCandidate(
            id = id,
            path = File(id).toPath(),
            storageClass = STORAGE_CLASS,
            bytes = sizeOf(id),
            lastUsed = createdAt,
            tier = RetentionTier.DELETE,
            reason = "past the $ruleId window (age ${ageOf(createdAt, now).toDays()}d)"
        )

    private fun sizeOf(id: String): Long = driver.read(id)?.sizeBytes ?: 0L

    data class IntegrityReport(
        val checked: List<String>,
        val corrupt: List<String>,
        val unverifiable: List<String>
    ) {
        val sound: Boolean get() = corrupt.isEmpty()

        fun render(): String = buildString {
            append(if (sound) "integrity OK" else "INTEGRITY FAILED")
            append(" — ${checked.size} checked")
            if (corrupt.isNotEmpty()) append(", ${corrupt.size} corrupt")
            if (unverifiable.isNotEmpty()) append(", ${unverifiable.size} not content-addressed")
        }

        fun auditLines(): List<String> =
            corrupt.map { "corrupt $it — content does not hash to its address" } +
                unverifiable.map { "unverifiable $it — opaque id, no expected digest" }
    }

    companion object {
        const val STORAGE_CLASS: String = "blobs"

        /** Where [defaultDriver] keeps blobs, relative to the repository root. */
        const val BLOB_DIR: String = ".atropos/blobs"

        /**
         * Matches the ceiling `StorageConstitution` declares, so the tracker
         * and the operator's `/storage status` agree on what "full" means.
         */
        const val DEFAULT_CEILING_BYTES: Long = 2L * 1024 * 1024 * 1024

        /**
         * How far behind now the safe-deletion boundary sits when there is no
         * pressure. A boundary at exactly now would collect an object the
         * instant its window elapsed, leaving no room for a run that is about
         * to reference it.
         */
        private val EMERGENCY_MARGIN: Duration = Duration.ofHours(1)

        private val CONTENT_ADDRESS = Regex("^[0-9a-f]{64}$")

        fun defaultDriver(repoRoot: java.nio.file.Path): StorageDriver =
            LocalDiskStorageDriver(repoRoot.resolve(BLOB_DIR).toFile())

        /**
         * The declared classes, in the shape [GcPolicyEnforcer] consumes.
         *
         * `isPermanent` carries `reclaimable = false` across, so `secrets` and
         * `config` stay uncollectable through this path too. `maxAge` is the
         * cold window: warm is about whether content stays inspectable, which
         * is not a question the blob layer answers.
         */
        fun rulesFrom(policy: RetentionPolicy): Map<String, StorageRetentionRule> =
            (policy.declared() + (DEFAULT_RULE_ID to RetentionRule.CONSERVATIVE))
                .associate { (name, rule) ->
                    name to StorageRetentionRule(
                        ruleId = name,
                        maxAge = rule.coldFor.takeIf { rule.reclaimable },
                        isPermanent = !rule.reclaimable
                    )
                }

        /** What [LocalDiskStorageDriver] stamps on blobs with no declared class. */
        const val DEFAULT_RULE_ID: String = "default-rule"
    }
}
