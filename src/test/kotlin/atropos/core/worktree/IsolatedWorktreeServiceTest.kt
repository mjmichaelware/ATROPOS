package atropos.core.worktree

import atropos.core.memory.LocalMemoryStore
import java.nio.file.Files
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IsolatedWorktreeServiceTest {
    @Test
    fun writeFile_refuses_blank_content_before_touching_worktree() {
        val root = Files.createTempDirectory("atropos-worktree-empty-write-")
        initializeGitRepo(root)
        val memory = LocalMemoryStore(root.resolve(".atropos/memory").toFile(), env = emptyMap())
        val service = IsolatedWorktreeService(root, memoryStore = memory)
        val created = service.createWorktree("job-empty-write", listOf("src"))
        val record = created.record ?: error(created.message)

        assertTrue(!service.writeFile(record.id, "src/Empty.kt", "   "))
        assertTrue(!Files.exists(record.worktreePath.resolve("src/Empty.kt")))
        assertTrue(memory.findBySubject("territory_violation", record.id).any { it.tags.contains("denied") })
    }

    @Test
    fun writeFile_refuses_parent_escape_before_touching_worktree() {
        val root = Files.createTempDirectory("atropos-worktree-parent-escape-")
        initializeGitRepo(root)
        val memory = LocalMemoryStore(root.resolve(".atropos/memory").toFile(), env = emptyMap())
        val service = IsolatedWorktreeService(root, memoryStore = memory)
        val created = service.createWorktree("job-parent-escape", listOf("src"))
        val record = created.record ?: error(created.message)
        val outside = record.worktreePath.parent.resolve("escaped.kt")

        assertTrue(!service.writeFile(record.id, "src/../escaped.kt", "package escaped"))
        assertTrue(!Files.exists(outside))
        assertTrue(memory.findBySubject("territory_violation", record.id).any {
            it.body.contains("src/../escaped.kt") && it.tags.contains("denied")
        })
    }

    @Test
    fun createWorktreeFailsWhenBaselineCommitIsUnavailable() {
        val root = Files.createTempDirectory("atropos-worktree-no-baseline-")
        ProcessBuilder("git", "init")
            .directory(root.toFile())
            .redirectErrorStream(true)
            .start()
            .waitFor()
        val service = IsolatedWorktreeService(root, memoryStore = LocalMemoryStore(root.resolve(".atropos/memory").toFile(), env = emptyMap()))

        val result = service.createWorktree("job-no-baseline", listOf("src/main/kotlin/atropos"))

        assertFalse(result.ok)
        assertTrue(result.message.contains("baseline commit unavailable"), result.message)
    }

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
        assertTrue(memory.findBySubject("territory_violation", "wt-test").any {
            it.body.contains("src/main/kotlin/atropos/core/provider/Leak.kt") &&
                it.tags.contains("denied")
        })
    }

    @Test
    fun verifyAndMerge_refuses_a_clean_worktree_without_marking_it_verified() {
        val root = Files.createTempDirectory("atropos-worktree-empty-merge-")
        initializeGitRepo(root)
        val service = IsolatedWorktreeService(
            root,
            memoryStore = LocalMemoryStore(root.resolve(".atropos/memory").toFile(), env = emptyMap())
        )

        val created = service.createWorktree("job-empty-merge", listOf("src"))
        val record = created.record ?: error(created.message)

        val result = service.verifyAndMerge(record.id)

        assertFalse(result.ok, result.message)
        assertTrue(result.message.contains("no source diff"), result.message)
        val persisted = service.readWorktree(record.id) ?: error("missing worktree record")
        assertFalse(persisted.verified)
        assertFalse(persisted.mergedBack)
        assertTrue(Files.exists(record.worktreePath), "failed verification must retain the worktree for inspection")
    }

    @Test
    fun intentToAdd_refuses_secret_bearing_staged_bytes_before_git_add() {
        val root = Files.createTempDirectory("atropos-worktree-staged-secret-")
        initializeGitRepo(root)
        val service = IsolatedWorktreeService(root)
        val created = service.createWorktree("job-secret", listOf("src"))
        val record = created.record ?: error(created.message)
        val target = record.worktreePath.resolve("src/Config.kt")
        Files.createDirectories(target.parent)
        Files.writeString(target, "package test\nconst val OPENAI_API_KEY = \"sk-ABCDEFGHIJKLMNOPQRSTUVWX\"\n")

        val result = service.intentToAdd(record.id, "src/Config.kt")

        assertTrue(!result.ok, result.message)
        assertTrue(result.message.contains("secret-bearing"), result.message)
    }

    @Test
    fun verifyAndMerge_refuses_unbounded_shell_verification_command() {
        val root = Files.createTempDirectory("atropos-worktree-command-injection-")
        initializeGitRepo(root)
        val memory = LocalMemoryStore(root.resolve(".atropos/memory").toFile(), env = emptyMap())
        val service = IsolatedWorktreeService(root, memoryStore = memory)
        val created = service.createWorktree("job-command-injection", listOf("src"))
        val record = created.record ?: error(created.message)
        val marker = root.resolve("shell-escaped.txt")

        val result = service.verifyAndMerge(
            record.id,
            "git diff --check; touch ${marker.fileName}"
        )

        assertTrue(!result.ok)
        assertTrue(result.message.contains("verification command refused"), result.message)
        assertTrue(!Files.exists(marker))
        assertTrue(!service.readWorktree(record.id)!!.verified)
    }

    private fun initializeGitRepo(repoRoot: java.nio.file.Path) {
        git(repoRoot, "init")
        Files.createDirectories(repoRoot.resolve("src"))
        Files.writeString(repoRoot.resolve("src/Seed.kt"), "const val SEED = true\n")
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
