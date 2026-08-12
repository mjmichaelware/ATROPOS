package atropos.core.factory

import atropos.core.provider.ContextEnvelopeFactory
import atropos.core.memory.LocalMemoryStore
import atropos.core.memory.MemoryKind
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class FactoryLineageTest {
    @Test
    fun prompt_is_hashed_dated_fingerprinted_and_spanned_before_requirements() {
        val root = Files.createTempDirectory("atropos-lineage-")
        val spec = AppProjectSpecParser().parse("Build a calculator CLI with tests")
        val lineage = FactoryLineageFactory.prepare(root, "factory-1", spec.prompt, spec)
        val prompt = Files.readString(root.resolve(".atropos/research/factory/factory-1/user-prompt.md"))
        val requirements = Files.readString(root.resolve(".atropos/research/factory/factory-1/requirements.md"))
        assertContains(prompt, "prompt_fingerprint=${lineage.promptFingerprint}")
        assertContains(prompt, "timestamp_utc=")
        assertContains(requirements, "prompt_spans=Build@")
        assertContains(requirements, "CLI@")
        assertTrue(lineage.promptSha256.matches(Regex("[0-9a-f]{64}")))
    }

    @Test
    fun prompt_artifact_is_recorded_in_run_memory_before_research() {
        val root = Files.createTempDirectory("atropos-lineage-memory-")
        val memory = LocalMemoryStore(root.resolve("memory").toFile())
        val spec = AppProjectSpecParser().parse("Build a calculator CLI with tests")
        val lineage = FactoryLineageFactory.prepare(root, "factory-memory", spec.prompt, spec, runMemory = memory)

        val promptRecord = memory.findBySubject("factory-prompt", "factory-memory").single()
        assertContains(promptRecord.body, "prompt_fingerprint=${lineage.promptFingerprint}")
        assertContains(promptRecord.body, "prompt_sha256=${lineage.promptSha256}")
    }

    @Test
    fun low_confidence_refuses_scaffold_with_yes_no_questions() {
        val root = Files.createTempDirectory("atropos-confidence-")
        val spec = AppProjectSpec("unclear", AppIntent("generated-app", "", emptyList()), true)
        val failure = assertFailsWith<FactoryClarificationRequired> {
            FactoryLineageFactory.prepare(root, "factory-low", spec.prompt, spec)
        }
        assertTrue(failure.message!!.contains("YES/NO:"))
        assertTrue(Files.exists(root.resolve(".atropos/research/factory/factory-low/user-prompt.md")))
        assertTrue(Files.exists(root.resolve(".atropos/research/factory/factory-low/requirements.md")))
        assertContains(
            Files.readString(root.resolve(".atropos/research/factory/factory-low/requirements.md")),
            "provider_suggestions=SKIPPED_SOFT_FAIL"
        )
        assertContains(failure.request.promptFingerprint, "prompt-")
        assertTrue(Files.exists(root.resolve(".atropos/research/factory/factory-low/clarification-questions.md")))
        val answersHash = FactoryClarificationRequest.persistAnswers(
            root.resolve(".atropos/research/factory/factory-low"),
            failure.request,
            listOf(true, false)
        )
        assertTrue(answersHash.matches(Regex("[0-9a-f]{64}")))
        assertContains(
            Files.readString(root.resolve(".atropos/research/factory/factory-low/clarification-answers.md")),
            "prompt_fingerprint=${failure.request.promptFingerprint}"
        )
        assertContains(
            Files.readString(root.resolve(".atropos/research/factory/factory-low/clarification-answers.md")),
            "timestamp_utc="
        )
    }

    @Test
    fun unavailable_research_channels_are_recorded_as_soft_failures() {
        val root = Files.createTempDirectory("atropos-research-")
        val spec = AppProjectSpecParser().parse("Build a notes CLI")
        val lineage = FactoryLineageFactory.prepare(root, "factory-research", spec.prompt, spec)
        assertContains(lineage.researchDocument, "lakehouse=")
        assertContains(lineage.researchDocument, "dloi=")
        assertContains(lineage.researchDocument, "bounded_fetch=")
        assertContains(lineage.researchDocument, "provider_suggestions=")
        assertContains(lineage.researchDocument, "specgraph=")
        assertContains(lineage.researchDocument, "internal DAG fallback")
    }

    @Test
    fun factory_context_carries_lineage_atoms_and_territory() {
        val root = Files.createTempDirectory("atropos-context-")
        val envelope = ContextEnvelopeFactory.createForFactory(
            projectId = "factory-1",
            promptFingerprint = "prompt-1234567890abcdef",
            researchSha256 = "a".repeat(64),
            atomIds = listOf("atom-1"),
            territory = listOf(".atropos/generated-projects/notes"),
            repoRoot = root
        )
        assertContains(envelope.task, "prompt-1234567890abcdef")
        assertContains(envelope.task, "atom-1")
        assertContains(envelope.assignedTerritory, ".atropos/generated-projects/notes")
        assertTrue(envelope.canonicalContextHash.matches(Regex("[0-9a-f]{64}")))
    }

    @Test
    fun long_term_research_rejects_unscoped_factory_memory() {
        val root = Files.createTempDirectory("atropos-memory-scope-")
        val memory = LocalMemoryStore(root.resolve("memory").toFile())
        memory.remember(
            MemoryKind.DECISION,
            "calculator decision",
            "project_id=project-a repository=${root.fileName}",
            tags = listOf("factory", "project-a")
        )
        memory.remember(
            MemoryKind.DECISION,
            "calculator decision",
            "project_id=project-b repository=${root.fileName}",
            tags = listOf("factory", "project-b")
        )

        val report = FactoryResearchService(memory).collect(root, "calculator", projectId = "project-a")

        assertContains(report.channelLog.first { it.startsWith("st_memory=") }, "scoped_hits=1", message = report.channelLog.joinToString())
        assertContains(report.channelLog.first { it.startsWith("st_memory=") }, "rejected=1")
        assertContains(report.channelLog.first { it.startsWith("lt_memory=") }, "scoped_records=1")
    }
}
