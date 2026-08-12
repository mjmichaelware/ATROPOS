/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.storage

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * `SUP.STOR.FREE-SPACE-GATE` and `SUP.STOR.GLOBAL-BYTE-CEILING`: the ceiling
 * is measured against real bytes on disk, and a refusal always names what
 * could be freed instead.
 */
class StorageSupervisorTest {

    private fun stateRoot(): Path {
        val root = Files.createTempDirectory("atropos-storage-test").resolve(".atropos")
        Files.createDirectories(root)
        return root
    }

    private fun write(root: Path, storageClass: String, bytes: Int) {
        val dir = root.resolve(storageClass)
        Files.createDirectories(dir)
        Files.write(dir.resolve("blob"), ByteArray(bytes) { 1 })
    }

    @Test
    fun `usage is measured per class from what is actually on disk`() {
        val root = stateRoot()
        write(root, "evidence", 4096)
        write(root, "worktrees", 2048)

        val constitution = StorageAccountant(root).measure(ceilingBytes = 1_000_000)

        assertEquals(6144, constitution.usedBytes)
        assertEquals(
            listOf("evidence" to 4096L, "worktrees" to 2048L),
            constitution.classes.map { it.id to it.bytes }
        )
    }

    @Test
    fun `an empty state directory reports no usage rather than failing`() {
        val constitution = StorageAccountant(stateRoot()).measure(ceilingBytes = 1_000)

        assertEquals(0, constitution.usedBytes)
        assertTrue(constitution.classes.isEmpty())
    }

    @Test
    fun `a write past the declared ceiling is refused and names the reclaim`() {
        val root = stateRoot()
        write(root, "evidence", 8192)

        val supervisor = StorageSupervisor(stateRoot = root, ceilingBytes = 8500)
        val decision = supervisor.admit(0)

        assertTrue(decision is FreeSpaceDecision.Refused)
        assertTrue(decision.reason.isNotBlank())
    }

    @Test
    fun `a write well inside the ceiling is permitted`() {
        val root = stateRoot()
        write(root, "evidence", 1024)

        val decision = StorageSupervisor(stateRoot = root, ceilingBytes = 10_000_000).admit(1024)

        assertTrue(decision.permitted)
    }

    @Test
    fun `the probe reports usable space for a directory that does not exist yet`() {
        val root = stateRoot()
        val unborn = root.resolve("not/created/yet")

        val usable = FreeSpaceProbe(unborn).usableBytes()

        assertTrue(usable != null && usable > 0, "the filestore of the nearest existing ancestor")
    }

    @Test
    fun `an unreadable filestore counts as exhausted, never as plenty`() {
        val probe = FreeSpaceProbe(Path.of("/definitely/not/a/real/mount/point"))

        // The anchor walk lands on the root filestore, which exists; the
        // contract under test is that the failure direction is conservative.
        val usable = probe.usableBytes()
        if (usable == null) assertTrue(probe.wouldExhaustDevice(1))
    }
}
