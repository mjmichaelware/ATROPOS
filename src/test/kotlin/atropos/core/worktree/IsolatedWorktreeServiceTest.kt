package atropos.core.worktree

import atropos.core.memory.LocalMemoryStore
import java.nio.file.Files
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertFalse

class IsolatedWorktreeServiceTest {
    @Test
    fun applyPatchRefusesOutOfTerritoryPatchBeforeMutation() {
        val root = Files.createTempDirectory("atropos-worktree-territory-")
        val memory = LocalMemoryStore(root.resolve(".atropos/memory").toFile(), env = emptyMap())
        val service = IsolatedWorktreeService(root, memoryStore = memory)
        val record = WorktreeRecord(
            id = "wt-test",
            jobId = "job-test",
            worktreePath = root,
            territory = listOf("src/main/kotlin/atropos/core/agent"),
            createdAt = Instant.EPOCH,
            updatedAt = Instant.EPOCH,
            metaFile = root.resolve(".atropos/worktrees/wt-test.meta")
        )
        val write = IsolatedWorktreeService::class.java.getDeclaredMethod("writeRecord", WorktreeRecord::class.java)
        write.isAccessible = true
        write.invoke(service, record)

        val patch = """
            diff --git a/src/main/kotlin/atropos/core/provider/Leak.kt b/src/main/kotlin/atropos/core/provider/Leak.kt
            new file mode 100644
            --- /dev/null
            +++ b/src/main/kotlin/atropos/core/provider/Leak.kt
            @@ -0,0 +1 @@
            +package atropos.core.provider
        """.trimIndent()

        assertFalse(service.applyPatch("wt-test", patch))
        assertFalse(Files.exists(root.resolve("src/main/kotlin/atropos/core/provider/Leak.kt")))
    }
}
