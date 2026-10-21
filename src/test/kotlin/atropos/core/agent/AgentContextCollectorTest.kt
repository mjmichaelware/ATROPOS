package atropos.core.agent

import atropos.core.policy.BoundedProcessRunner
import java.nio.file.Files
import kotlin.test.assertEquals
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
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
    fun task_source_pack_uses_referenced_files_instead_of_the_entire_external_tree() {
        val root = Files.createTempDirectory("atropos-context-pack-task-files-")
        Files.createDirectories(root.resolve("src"))
        Files.writeString(root.resolve("src/target.py"), "# referenced source\n" + "value = 1\n".repeat(2_000))
        Files.writeString(root.resolve("src/unrelated.py"), "x\n".repeat(100_000))

        val snapshot = AgentContextCollector(repoRoot = root, contextCapBytes = 64 * 1024)
            .collectPatch("fix src/target.py")

        assertFalse(snapshot.truncated, snapshot.text)
        assertEquals(null, snapshot.sourcePackFailure)
        assertTrue(snapshot.sourcePackId != null, snapshot.text)
        assertTrue(snapshot.text.contains("FILE src/target.py"), snapshot.text)
        assertFalse(snapshot.text.contains("FILE src/unrelated.py"), snapshot.text)
    }

    @Test
    fun free_form_task_hint_does_not_fabricate_an_empty_target_workspace() {
        // Mirrors astropy__astropy-11693: the task is the issue text, the
        // workspace is the current repository root, and the referenced source
        // files live under it. A free-form hint must not be turned into an
        // empty temporary target whose only candidate relativises to `..`.
        val root = Files.createTempDirectory("atropos-context-pack-freeform-")
        Files.createDirectories(root.resolve("astropy/wcs/wcsapi"))
        Files.writeString(root.resolve("astropy/wcs/wcs.py"), "world = 1\n".repeat(200))
        Files.writeString(root.resolve("astropy/wcs/wcsapi/fitswcs.py"), "api = 1\n".repeat(50))

        val task = "WCS.all_world2pix failed to converge when plotting; see " +
            "astropy/wcs/wcsapi/fitswcs.py and astropy/wcs/wcs.py"

        val snapshot = AgentContextCollector(repoRoot = root, contextCapBytes = 64 * 1024).collectPatch(task)

        assertEquals(null, snapshot.sourcePackFailure, snapshot.sourcePackDiagnostics)
        assertTrue(snapshot.sourcePackId != null, snapshot.sourcePackDiagnostics)
        assertTrue(snapshot.text.contains("FILE astropy/wcs/wcs.py"), snapshot.text)
        assertTrue(snapshot.text.contains("FILE astropy/wcs/wcsapi/fitswcs.py"), snapshot.text)
    }

    @Test
    fun prompt_file_hint_is_scanned_for_references_and_never_becomes_the_target_workspace() {
        // The evaluator prompt is a file, not a workspace. It must be read for
        // referenced paths, but the file path itself must not be adopted as the
        // target root (which would fail with "local_path is not a directory").
        val root = Files.createTempDirectory("atropos-context-pack-prompt-")
        Files.createDirectories(root.resolve("astropy/wcs"))
        Files.writeString(root.resolve("astropy/wcs/wcs.py"), "value = 1\n".repeat(100))

        val prompt = Files.createTempFile("astropy__astropy-11693_prompt-", ".txt")
        Files.writeString(
            prompt,
            "Astropy issue: WCS.all_world2pix failed to converge.\n" +
                "Referenced files: astropy/wcs/wcs.py and astropy/wcs/wcsapi/fitswcs.py\n"
        )

        val snapshot = AgentContextCollector(repoRoot = root, contextCapBytes = 64 * 1024)
            .collectPatch(prompt.toString())

        assertEquals(null, snapshot.sourcePackFailure, snapshot.sourcePackDiagnostics)
        assertTrue(snapshot.sourcePackId != null, snapshot.sourcePackDiagnostics)
        assertTrue(snapshot.text.contains("FILE astropy/wcs/wcs.py"), snapshot.text)
    }

    @Test
    fun source_pack_failure_carries_redacted_diagnostics() {
        val root = Files.createTempDirectory("atropos-context-pack-diagnostics-")

        val snapshot = AgentContextCollector(repoRoot = root, contextCapBytes = 64 * 1024)
            .collectPatch("fix src/main/kotlin/atropos/missing/Missing.kt")

        assertTrue(snapshot.sourcePackFailure != null, snapshot.sourcePackDiagnostics)
        val diagnostics = assertNotNull(snapshot.sourcePackDiagnostics)
        assertTrue(diagnostics.startsWith("pack target="), diagnostics)
        assertTrue(diagnostics.contains("binding="), diagnostics)
        assertTrue(diagnostics.contains("allowed="), diagnostics)
        assertTrue(diagnostics.contains("outcome="), diagnostics)
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
