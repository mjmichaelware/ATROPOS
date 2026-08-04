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
        assertTrue(
            Files.readString(root.resolve(".atropos/policy/audit.log")).contains("action=FILE_MUTATION"),
            "self-host mutation must leave bounded-agency evidence"
        )
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

    @Test
    fun refuses_mutation_that_produces_no_source_diff() {
        val root = Files.createTempDirectory("atropos-self-host-worktree-empty-diff-")
        initializeGitRepo(root)
        val store = DagStore(root)
        val path = "src/main/kotlin/atropos/core/agent/SelfHostNoDiff.kt"
        val content = "package atropos.core.agent\nobject SelfHostNoDiff { const val VALUE: String = \"same\" }"
        Files.writeString(root.resolve(path), "$content\n")
        git(root, "add", ".")
        git(root, "commit", "-m", "seed no-diff file")
        val node = DagNode(
            id = "node-worktree-empty-diff",
            label = "Worktree empty diff refuse",
            territory = listOf("src/main/kotlin/atropos/core/agent"),
            action = DagNodeAction.EDIT_FILE,
            actionPayload = "$path::$content",
            expectedOutputs = listOf(path),
            createdAt = Instant.parse("2026-07-29T00:09:00Z"),
            updatedAt = Instant.parse("2026-07-29T00:09:00Z"),
            metaFile = root.resolve(".atropos/dag/node-worktree-empty-diff.meta")
        )
        store.writeNode(node)

        val result = SelfHostWorktreeNodeExecutor(root, dagStore = store).execute(node)

        assertTrue(!result.ok)
        assertTrue(result.message.contains("produced no source diff"), result.message)
        assertEquals(DagNodeState.FAILED, store.readNode(node.id)?.state)
    }

    @Test
    fun refuses_in_territory_mutation_when_output_is_not_declared() {
        val root = Files.createTempDirectory("atropos-self-host-worktree-output-refuse-")
        initializeGitRepo(root)
        val store = DagStore(root)
        val unexpected = "src/main/kotlin/atropos/core/agent/Unexpected.kt"
        val node = DagNode(
            id = "node-worktree-output-refuse",
            label = "Worktree undeclared output refuse",
            territory = listOf("src/main/kotlin/atropos/core/agent"),
            action = DagNodeAction.EDIT_FILE,
            actionPayload = "$unexpected::package atropos.core.agent\nobject Unexpected",
            expectedOutputs = listOf("src/main/kotlin/atropos/core/agent/Declared.kt"),
            createdAt = Instant.parse("2026-07-29T00:10:00Z"),
            updatedAt = Instant.parse("2026-07-29T00:10:00Z"),
            metaFile = root.resolve(".atropos/dag/node-worktree-output-refuse.meta")
        )
        store.writeNode(node)

        val result = SelfHostWorktreeNodeExecutor(root, dagStore = store).execute(node)

        assertTrue(!result.ok)
        assertTrue(result.message.contains("not a declared expected output"), result.message)
        assertTrue(!Files.exists(root.resolve(unexpected)))
        assertEquals(DagNodeState.FAILED, store.readNode(node.id)?.state)
    }

    private fun initializeGitRepo(repoRoot: java.nio.file.Path) {
        git(repoRoot, "init")
        Files.createDirectories(repoRoot.resolve("src/main/kotlin/atropos/core/agent"))
        Files.writeString(repoRoot.resolve("src/main/kotlin/atropos/Main.kt"), "fun main() {}\n")
        git(repoRoot, "config", "user.email", "atropos@example.invalid")
        git(repoRoot, "config", "user.name", "ATROPOS Test")
        git(repoRoot, "add", ".")
        git(repoRoot, "commit", "-m", "initial")
    }

    private fun git(repoRoot: java.nio.file.Path, vararg args: String) {
        val process = ProcessBuilder(listOf("git") + args)
            .directory(repoRoot.toFile())
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().readText()
        val exit = process.waitFor()
        check(exit == 0) { "git ${args.joinToString(" ")} failed: $output" }
    }
}
