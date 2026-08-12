/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.storage

import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant

/**
 * Reclaims evidence bundles once nothing can still need them.
 *
 * `SUP.STOR.EVIDENCE-BUNDLE-GC`: "Evidence remains available for the exact
 * window needed for verification and audit, then reclaims."
 *
 * Deliberately parallel to [WorktreeGc] — same triggers, same policy, same
 * outcome type — and deliberately a separate class. The two hold different
 * safety rules, and the evidence rule is the stricter one: a bundle that
 * [atropos.core.verifier] or an auditor replay would want is unrecoverable
 * once removed, because it is a record of something that already happened and
 * cannot be re-run to produce it again.
 *
 * The atom requires that GC "never removes a bundle required for
 * VerifiedCompletionGate or auditor replay". Both are expressed through
 * [requiredBundleIds], supplied by the caller. Evidence is also held when it is
 * referenced by a fingerprint: [atropos.core.auth.FingerprintStore] records
 * attest that a specific artifact was seen, and dropping the bundle behind an
 * attestation would leave a claim nothing supports.
 */
class EvidenceBundleGc(
    private val evidenceRoot: Path,
    private val reclaimer: StorageReclaimer,
    private val policy: RetentionPolicy = RetentionPolicy()
) {
    /**
     * @param requiredBundleIds bundles a gate, an auditor replay, or an open
     *   investigation still needs. Held unconditionally.
     * @param fingerprintedIds bundles referenced by a recorded fingerprint.
     */
    fun collect(
        requiredBundleIds: Set<String> = emptySet(),
        fingerprintedIds: Set<String> = emptySet(),
        pressure: Double = 0.0,
        dryRun: Boolean = true,
        now: Instant = Instant.now()
    ): GcOutcome {
        val removed = mutableListOf<GcCandidate>()
        val retained = mutableListOf<GcRetention>()
        val failed = mutableListOf<String>()

        for (bundle in bundles()) {
            val id = bundle.fileName.toString()
            val bytes = reclaimer.sizeOf(bundle)

            val holder = when {
                id in requiredBundleIds -> "required by a gate, auditor replay, or open investigation"
                id in fingerprintedIds -> "referenced by a recorded fingerprint"
                else -> null
            }
            if (holder != null) {
                retained += GcRetention(id, bytes, holder)
                continue
            }

            val lastUsed = lastModified(bundle)
            if (lastUsed == null) {
                // An unreadable timestamp makes age unknowable, and an unknown
                // age must not be treated as old. Held, and reported so the
                // operator can see why the collector left it.
                retained += GcRetention(id, bytes, "modification time unreadable; age unknown")
                continue
            }

            val tier = policy.tierFor(
                storageClass = STORAGE_CLASS,
                age = ageOf(lastUsed, now),
                referenced = false,
                pressure = pressure
            )
            if (tier != RetentionTier.DELETE) {
                retained += GcRetention(id, bytes, "still ${tier.canonical}: ${tier.description}")
                continue
            }

            val candidate = GcCandidate(
                id = id,
                path = bundle,
                storageClass = STORAGE_CLASS,
                bytes = bytes,
                lastUsed = lastUsed,
                tier = tier,
                reason = "no gate, replay or fingerprint references it, and it is past the " +
                    "${policy.ruleFor(STORAGE_CLASS).coldFor.toDays()}-day window"
            )

            if (dryRun) {
                removed += candidate
                continue
            }

            val freed = reclaimer.remove(bundle)
            if (freed == null) failed += "$id: $bundle could not be removed"
            else removed += candidate.copy(bytes = freed)
        }

        return GcOutcome(STORAGE_CLASS, removed, retained, failed, dryRun)
    }

    private fun bundles(): List<Path> {
        if (!Files.isDirectory(evidenceRoot)) return emptyList()
        return runCatching {
            Files.list(evidenceRoot).use { it.toList() }
        }.getOrDefault(emptyList())
    }

    private fun lastModified(path: Path): Instant? =
        runCatching { Files.getLastModifiedTime(path).toInstant() }.getOrNull()

    private companion object {
        const val STORAGE_CLASS = "evidence"
    }
}
