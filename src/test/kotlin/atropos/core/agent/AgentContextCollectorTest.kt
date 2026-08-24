package atropos.core.agent

import atropos.core.policy.BoundedProcessRunner
import java.nio.file.Files
import kotlin.test.assertEquals
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AgentContextCollectorTest {
    @Test
    fun imported_instruction_pack_is_redacted_attested_and_loaded_into_context() {
        val root = Files.createTempDirectory("atropos-imported-instructions-")
        val source = root.resolve(".github/copilot-instructions.md")
        Files.createDirectories(source.parent)
        Files.writeString(source, "use the bounded runner\nOPENAI_API_KEY=sk-live-abcdefghijklmnopqrstuvwxyz\n")

        val imported = ImportedInstructionPackStore(root).import(".github/copilot-instructions.md").getOrThrow()
        assertTrue(imported.hasValidContentHash())
        assertTrue(imported.text.contains("use the bounded runner"))
        assertFalse(imported.text.contains("sk-live-abcdefghijklmnopqrstuvwxyz"))

        val snapshot = AgentContextCollector(repoRoot = root).collectPatch("read README.md")
        assertTrue(snapshot.text.contains("Imported Instructions (context-only"), snapshot.text)
        assertTrue(snapshot.text.contains("use the bounded runner"), snapshot.text)
        assertFalse(snapshot.text.contains("sk-live-abcdefghijklmnopqrstuvwxyz"), snapshot.text)
    }

    @Test
    fun imported_instruction_pack_rejects_paths_outside_repository() {
        val root = Files.createTempDirectory("atropos-imported-instructions-boundary-")
        val outside = Files.createTempFile("atropos-outside-", ".md")
        val result = ImportedInstructionPackStore(root).import(outside.toString())
        assertTrue(result.isFailure)
    }

    @Test
    fun collectPatchRefusesExcludedSecretHintFiles() {
        val root = Files.createTempDirectory("atropos-context-secret-excluded-")
        Files.writeString(root.resolve(".env"), "OPENAI_API_KEY=sk-live-abcdefghijklmnopqrstuvwxyz\n")
        Files.writeString(root.resolve("README.md"), "readme\n")

        val snapshot = AgentContextCollector(repoRoot = root).collectPatch("edit .env")

        assertFalse(snapshot.text.contains("sk-live-abcdefghijklmnopqrstuvwxyz"), snapshot.text)
        assertFalse(snapshot.text.contains("FILE .env"), snapshot.text)
    }

    @Test
    fun collectPatchRedactsSecretTextFromAllowedHintFiles() {
        val root = Files.createTempDirectory("atropos-context-redacts-")
        Files.createDirectories(root.resolve("src/main/kotlin/atropos/core/agent"))
        Files.writeString(
            root.resolve("src/main/kotlin/atropos/core/agent/Hint.kt"),
            "package hint\nval token = \"sk-live-abcdefghijklmnopqrstuvwxyz\"\n"
        )

        val snapshot = AgentContextCollector(repoRoot = root).collectPatch("edit src/main/kotlin/atropos/core/agent/Hint.kt")

        assertTrue(snapshot.text.contains("FILE src/main/kotlin/atropos/core/agent/Hint.kt"), snapshot.text)
        assertTrue(snapshot.text.contains("<redacted:api_key>"), snapshot.text)
        assertFalse(snapshot.text.contains("sk-live-abcdefghijklmnopqrstuvwxyz"), snapshot.text)
    }

    @Test
    fun collectPreservesUtf8WhenContextIsTruncated() {
        val root = Files.createTempDirectory("atropos-context-utf8-")
        Files.writeString(root.resolve("README.md"), "🚀".repeat(400))

        val snapshot = AgentContextCollector(repoRoot = root, contextCapBytes = 180).collectPatch("edit README.md")

        assertTrue(snapshot.truncated)
        assertTrue(snapshot.byteCount <= 180, "context byte count exceeded cap: ${snapshot.byteCount}")
        assertFalse(snapshot.text.contains('\uFFFD'), snapshot.text)
    }

    @Test
    fun code_context_refuses_a_truncated_source_pack() {
        val root = Files.createTempDirectory("atropos-context-pack-truncated-")
        val file = root.resolve("src/main/kotlin/atropos/core/agent/Large.kt")
        Files.createDirectories(file.parent)
        Files.writeString(file, "package atropos.core.agent\n" + "val payload = \"x\"\n".repeat(2_000))

        val snapshot = AgentContextCollector(repoRoot = root, contextCapBytes = 40 * 1024)
            .collectPatch("edit src/main/kotlin/atropos/core/agent/Large.kt")

        assertTrue(snapshot.truncated)
        assertEquals("SOURCE_PACK_TRUNCATED", snapshot.sourcePackFailure)
        assertTrue(snapshot.sourcePackId == null)
    }

    @Test
    fun gitStatusUsesBoundedRunnerAndPreservesLiteralArguments() {
        val root = Files.createTempDirectory("atropos-context-runner-argv-")
        var observedCommand: List<String> = emptyList()
        val runner = BoundedProcessRunner { command, _, _, _ ->
            observedCommand = command
            ProcessBuilder("printf", "status\\n").start()
        }

        val snapshot = AgentContextCollector(
            repoRoot = root,
            processRunner = runner
        ).collect()

        assertEquals(listOf("git", "status", "--short", "--branch"), observedCommand)
        assertTrue(snapshot.text.contains("status"), snapshot.text)
    }

    @Test
    fun gitStatusTimeoutBecomesTypedUnavailableContext() {
        val root = Files.createTempDirectory("atropos-context-runner-timeout-")
        val runner = BoundedProcessRunner { _, _, _, _ ->
            ProcessBuilder("sleep", "1").start()
        }

        val snapshot = AgentContextCollector(
            repoRoot = root,
            processRunner = runner,
            commandTimeoutMillis = 1L
        ).collect()

        assertTrue(snapshot.text.contains("git timed out"), snapshot.text)
    }

    @Test
    fun gitStatusOutputIsBoundedByCollectorContextCap() {
        val root = Files.createTempDirectory("atropos-context-runner-output-")
        val runner = BoundedProcessRunner { _, _, _, _ ->
            ProcessBuilder("printf", "x\nx\n".repeat(200)).start()
        }

        val snapshot = AgentContextCollector(
            repoRoot = root,
            contextCapBytes = 4 * 1024,
            processRunner = runner,
            commandOutputLines = 1
        ).collect()

        assertTrue(snapshot.text.contains("[command output truncated]"), snapshot.text)
        assertTrue(snapshot.byteCount <= 4 * 1024, "context byte count exceeded cap: ${snapshot.byteCount}")
    }
}
