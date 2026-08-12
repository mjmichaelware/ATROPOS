/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.storage

import atropos.core.worktree.WorktreeRecord
import atropos.core.worktree.WorktreeRecordStore
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * `SUP.STOR.WORKTREE-GC` and `SUP.STOR.EVIDENCE-BUNDLE-GC`.
 *
 * The assertions that matter are the refusals. A collector that reclaims is
 * easy; a collector that reclaims under pressure *without* taking the record of
 * the run currently executing is the thing the atoms actually require.
 */
class StorageCollectionTest {

    private fun workspace(): Path = Files.createTempDirectory("atropos-gc-test")

    private fun worktree(
        store: WorktreeRecordStore,
        root: Path,
        id: String,
        jobId: String,
        ageDays: Long,
        merged: Boolean = false,
        rolledBack: Boolean = false,
        verified: Boolean = false
    ): WorktreeRecord {
        val dir = root.resolve(id)
        Files.createDirectories(dir)
        Files.writeString(dir.resolve("work.txt"), "x".repeat(64))
        val at = Instant.now().minusSeconds(ageDays * 86_400)
        val record = WorktreeRecord(
            id = id,
            jobId = jobId,
            worktreePath = dir,
            verified = verified,
            rolledBack = rolledBack,
            mergedBack = merged,
            createdAt = at,
            updatedAt = at,
            metaFile = store.metaFileFor(id)!!
        )
        store.write(record)
        return record
    }

    @Test
    fun `an old merged worktree is collected and its record removed`() {
        val root = workspace()
        val store = WorktreeRecordStore(root)
        store.ensureRoot()
        worktree(store, root, "wt-merged", "job-1", ageDays = 60, merged = true)

        val outcome = WorktreeGc(store, StorageReclaimer(root)).collect(dryRun = false)

        assertEquals(listOf("wt-merged"), outcome.removed.map { it.id })
        assertTrue(outcome.reclaimedBytes > 0)
        assertNull(store.read("wt-merged"))
        assertFalse(Files.exists(root.resolve("wt-merged")))
    }

    @Test
    fun `an unresolved worktree is never collected however old it is`() {
        val root = workspace()
        val store = WorktreeRecordStore(root)
        store.ensureRoot()
        worktree(store, root, "wt-crashed", "job-2", ageDays = 900)

        val outcome = WorktreeGc(store, StorageReclaimer(root))
            .collect(pressure = 1.0, dryRun = false)

        assertTrue(outcome.removed.isEmpty())
        assertTrue(outcome.retained.single().heldBy.contains("unresolved"))
        assertTrue(Files.exists(root.resolve("wt-crashed")))
    }

    @Test
    fun `a worktree belonging to an open job is held`() {
        val root = workspace()
        val store = WorktreeRecordStore(root)
        store.ensureRoot()
        worktree(store, root, "wt-open", "job-3", ageDays = 400, merged = true)

        val outcome = WorktreeGc(store, StorageReclaimer(root))
            .collect(openRunIds = setOf("job-3"), pressure = 1.0, dryRun = false)

        assertTrue(outcome.removed.isEmpty())
        assertTrue(outcome.retained.single().heldBy.contains("job-3"))
    }

    @Test
    fun `verified but unmerged work is held because the evidence never landed`() {
        val root = workspace()
        val store = WorktreeRecordStore(root)
        store.ensureRoot()
        worktree(store, root, "wt-verified", "job-4", ageDays = 400, rolledBack = true, verified = true)

        val outcome = WorktreeGc(store, StorageReclaimer(root)).collect(dryRun = false)

        assertTrue(outcome.removed.isEmpty())
        assertTrue(outcome.retained.single().heldBy.contains("has not reached the tree"))
    }

    @Test
    fun `a dry run deletes nothing but reports what it would take`() {
        val root = workspace()
        val store = WorktreeRecordStore(root)
        store.ensureRoot()
        worktree(store, root, "wt-old", "job-5", ageDays = 60, merged = true)

        val outcome = WorktreeGc(store, StorageReclaimer(root)).collect()

        assertTrue(outcome.dryRun)
        assertEquals(listOf("wt-old"), outcome.removed.map { it.id })
        assertTrue(Files.exists(root.resolve("wt-old")))
        assertTrue(outcome.auditLines().any { it.startsWith("would-remove") })
    }

    @Test
    fun `evidence required by a gate or a fingerprint is never collected`() {
        val root = workspace()
        val evidence = root.resolve("evidence")
        Files.createDirectories(evidence)
        listOf("bundle-gate", "bundle-fp", "bundle-stale").forEach { name ->
            val file = evidence.resolve(name)
            Files.writeString(file, "evidence")
            Files.setLastModifiedTime(
                file,
                java.nio.file.attribute.FileTime.from(Instant.now().minusSeconds(400L * 86_400))
            )
        }

        val outcome = EvidenceBundleGc(evidence, StorageReclaimer(root)).collect(
            requiredBundleIds = setOf("bundle-gate"),
            fingerprintedIds = setOf("bundle-fp"),
            pressure = 1.0,
            dryRun = false
        )

        assertEquals(listOf("bundle-stale"), outcome.removed.map { it.id })
        assertEquals(
            setOf("bundle-gate", "bundle-fp"),
            outcome.retained.map { it.id }.toSet()
        )
        assertTrue(Files.exists(evidence.resolve("bundle-gate")))
    }

    @Test
    fun `the reclaimer refuses its own root`() {
        val root = workspace()
        Files.writeString(root.resolve("inside.txt"), "data")

        assertNull(StorageReclaimer(root).remove(root))
        assertTrue(Files.exists(root))
    }

    @Test
    fun `the reclaimer refuses a path outside its root`() {
        val root = workspace()
        val outside = Files.createTempDirectory("atropos-gc-outside")
        Files.writeString(outside.resolve("keep.txt"), "data")

        assertNull(StorageReclaimer(root).remove(outside))
        assertTrue(Files.exists(outside.resolve("keep.txt")))
    }
}
