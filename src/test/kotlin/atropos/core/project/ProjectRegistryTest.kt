package atropos.core.project

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProjectRegistryTest {
    @Test
    fun registersRepositoryBindingDurablyAndDeduplicatesByNameAndRoot() {
        val root = Files.createTempDirectory("atropos-project-registry-")
        val registry = ProjectRegistry(root)

        val first = registry.register(
            "tiny-cli",
            binding = RepositoryBinding(repoRoot = root.toString(), branch = "main", baselineCommit = "abc", dirtyFingerprint = "dirty")
        )
        val second = ProjectRegistry(root).register(
            "tiny-cli",
            binding = RepositoryBinding(repoRoot = root.toString(), branch = "main", baselineCommit = "abc", dirtyFingerprint = "dirty")
        )

        assertTrue(first.created)
        assertFalse(second.created)
        assertEquals(first.record.id, second.record.id)
        assertEquals(root.toString(), second.record.binding.repoRoot)
    }

    // §2.2: "Project identity is durable across restarts." §2.9: history is
    // permanent. The cases below hold the registry to both.

    @Test
    fun objectiveAndStatusSurviveANewRegistryInstance() {
        val root = Files.createTempDirectory("atropos-project-durable-")
        val created = ProjectRegistry(root).register(
            name = "cascade migration",
            objective = "move the provider cascade onto the bounded agency gate"
        )

        // A second instance stands in for a restart: nothing is held in memory.
        val reloaded = ProjectRegistry(root).resolve(created.record.id)

        assertEquals("cascade migration", reloaded?.name)
        assertEquals("move the provider cascade onto the bounded agency gate", reloaded?.objective)
        assertEquals(ProjectStatus.IDLE, reloaded?.status)
    }

    @Test
    fun statusUsesTheCanonicalVocabularySharedWithTheWebSurface() {
        assertEquals("review-required", ProjectStatus.REVIEW_REQUIRED.canonical)
        assertEquals(ProjectStatus.REVIEW_REQUIRED, ProjectStatus.fromCanonical("review-required"))
        assertEquals(ProjectStatus.BLOCKED, ProjectStatus.fromCanonical("BLOCKED"))
    }

    @Test
    fun historyRecordsEveryMutationAndIsNeverRewritten() {
        val root = Files.createTempDirectory("atropos-project-history-")
        val registry = ProjectRegistry(root)
        val project = registry.register("history check").record

        val working = registry.setStatus(project, ProjectStatus.WORKING)
        val linked = registry.linkWorkItem(working, "queue-20260729-100000-000-abcd1234")
        registry.setStatus(linked, ProjectStatus.REVIEW_REQUIRED)

        val history = registry.history(project.id)

        // Newest first, with the creation event still present at the end: a
        // later change never erases what came before (§4.0).
        assertEquals("status_changed", history.first().event)
        assertEquals("created", history.last().event)
        assertEquals(4, history.size)
        assertTrue(history.any { it.event == "work_linked" })
    }

    @Test
    fun completionWithoutEvidenceIsReportedAsUnverifiable() {
        val root = Files.createTempDirectory("atropos-project-completion-")
        val registry = ProjectRegistry(root)
        val project = registry.register("claims done").record

        // §3.4: completion requires evidence, not elapsed time.
        val completed = registry.setStatus(project, ProjectStatus.COMPLETED)
        assertFalse(completed.completionIsVerifiable)

        assertTrue(registry.linkEvidence(completed, "evidence-1").completionIsVerifiable)
    }

    @Test
    fun recordsWrittenBeforeTheNewColumnsExistedStillLoad() {
        val root = Files.createTempDirectory("atropos-project-legacy-")
        val index = root.resolve(".atropos/projects/projects.jsonl")
        Files.createDirectories(index.parent)
        fun b64(value: String) = java.util.Base64.getEncoder().encodeToString(value.toByteArray())
        // Exactly the nine columns the first release wrote.
        val legacy = listOf(
            "project-legacy0001", b64("legacy"), "app-factory",
            b64("/repo"), b64("main"), b64("abc123"), b64(""),
            "2026-07-01T00:00:00Z", "2026-07-01T00:00:00Z"
        ).joinToString("\t")
        Files.writeString(index, legacy + "\n", java.nio.charset.StandardCharsets.UTF_8)

        val loaded = ProjectRegistry(root).resolve("project-legacy0001")

        assertEquals("legacy", loaded?.name)
        // Absent columns take defaults rather than failing the whole read.
        assertEquals("", loaded?.objective)
        assertEquals(ProjectStatus.IDLE, loaded?.status)
        assertEquals(emptyList(), loaded?.workItemIds)
    }
}
