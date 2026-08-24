package atropos.core.factory

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

class FactoryRunHandoffTest {
    @Test
    fun handoff_records_next_atoms_freeze_and_last_good_commit() {
        val root = Files.createTempDirectory("factory-handoff")
        val freeze = FactoryAcceptanceFreeze.create(
            promptSha256 = "a".repeat(64),
            researchSha256 = "b".repeat(64),
            atomIds = listOf("atom-2", "atom-1"),
            promptSpans = "CLI@1-1|class=surface"
        )
        val snapshot = FactoryObligationSnapshot(
            openWork = 1,
            runnableAtomIds = listOf("atom-1"),
            blockedAtomIds = emptyList(),
            failedAtomIds = emptyList(),
            doneAtomIds = listOf("atom-0"),
            stopReason = null
        )

        val path = FactoryRunHandoff.write(root, "run-1", "dag-1", snapshot, freeze, "commit-1")
        val body = Files.readString(path)
        assertContains(body, "acceptance_freeze_sha256=${freeze.sha256}")
        assertContains(body, "next_runnable_atoms=atom-1")
        assertContains(body, "last_good_commit=commit-1")
        assertEquals(root.resolve(".atropos/runs/run-1/factory-handoff.md"), path)
        val restored = FactoryRunHandoff.read(root, "run-1")
        assertEquals("dag-1", restored.dagId)
        assertEquals(listOf("atom-1"), restored.nextRunnableAtomIds)
        assertEquals("commit-1", restored.lastGoodCommit)
    }
}
