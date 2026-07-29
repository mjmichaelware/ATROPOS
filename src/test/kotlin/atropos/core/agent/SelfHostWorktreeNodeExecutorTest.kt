package atropos.core.agent

import atropos.core.dag.DagNode
import atropos.core.dag.DagNodeAction
import atropos.core.dag.DagNodeState
import atropos.core.dag.DagStore
import java.nio.file.Files
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SelfHostWorktreeNodeExecutorTest {
    @Test
    fun executes_file_mutation_in_isolated_worktree_then_merges_hash_linked_result() {
        val root = Files.createTempDirectory("atropos-self-host-worktree-node-")
        initializeGitRepo(root)
        val store = DagStore(root)
        val path = "src/main/kotlin/atropos/core/agent/SelfHostWorktreeProof.kt"
        val node = DagNode(
            id = "node-worktree-proof",
            label = "Worktree proof",
            territory = listOf("src/main/kotlin/atropos/core/agent"),
            action = DagNodeAction.EDIT_FILE,
            actionPayload = "$path::package atropos.core.agent\nobject SelfHostWorktreeProof { const val PROVEN: Boolean = true }",
            expectedOutputs = listOf(path),
            createdAt = Instant.parse("2026-07-29T00:07:00Z"),
            updatedAt = Instant.parse("2026-07-29T00:07:00Z"),
            metaFile = root.resolve(".atropos/dag/node-worktree-proof.meta")
        )
        store.writeNode(node)

        val result = SelfHostWorktreeNodeExecutor(root, dagStore = store).execute(node)

        assertTrue(result.ok, result.message)
        assertEquals(DagNodeState.COMPLETE, result.state)
        assertTrue(Files.readString(root.resolve(path)).contains("PROVEN: Boolean = true"))
        assertTrue(result.result.orEmpty().contains("worktree="), result.result.orEmpty())
        assertTrue(result.result.orEmpty().contains("sha256="), result.result.orEmpty())
        assertEquals(DagNodeState.COMPLETE, store.readNode(node.id)?.state)
    }

    @Test
    fun refuses_out_of_territory_mutation_before_worktree_write() {
        val root = Files.createTempDirectory("atropos-self-host-worktree-refuse-")
        initializeGitRepo(root)
        val store = DagStore(root)
        val node = DagNode(
            id = "node-worktree-refuse",
            label = "Worktree refuse",
            territory = listOf("src/main/kotlin/atropos/core/agent"),
            action = DagNodeAction.EDIT_FILE,
            actionPayload = "apps/specgraph-foundry/package.json::{}",
            createdAt = Instant.parse("2026-07-29T00:08:00Z"),
            updatedAt = Instant.parse("2026-07-29T00:08:00Z"),
            metaFile = root.resolve(".atropos/dag/node-worktree-refuse.meta")
        )
        store.writeNode(node)

        val result = SelfHostWorktreeNodeExecutor(root, dagStore = store).execute(node)

        assertTrue(!result.ok)
        assertTrue(result.message.contains("territory violation before worktree mutation"), result.message)
        assertTrue(!Files.exists(root.resolve("apps/specgraph-foundry/package.json")))
        assertEquals(DagNodeState.FAILED, store.readNode(node.id)?.state)
    }

    private fun initializeGitRepo(repoRoot: java.nio.file.Path) {
        ProcessBuilder("git", "init")
            .directory(repoRoot.toFile())
            .redirectErrorStream(true)
            .start()
            .waitFor()
        Files.createDirectories(repoRoot.resolve("src/main/kotlin/atropos/core/agent"))
        Files.writeString(repoRoot.resolve("src/main/kotlin/atropos/Main.kt"), "fun main() {}\n")
        listOf(
            listOf("git", "config", "user.email", "atropos@example.invalid"),
            listOf("git", "config", "user.name", "ATROPOS Test"),
            listOf("git", "add", "."),
            listOf("git", "commit", "-m", "initial")
        ).forEach { command ->
            ProcessBuilder(command)
                .directory(repoRoot.toFile())
                .redirectErrorStream(true)
                .start()
                .waitFor()
        }
    }
}
