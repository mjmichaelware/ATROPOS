package atropos.cli.ui

import atropos.cli.ui.design.Health
import atropos.cli.ui.design.RunState
import atropos.core.agent.AgentQueueCheckpoint
import atropos.core.agent.AgentQueueState
import atropos.core.agent.AgentQueueStore
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Source Document 4 §0.1 requires Home to answer six questions. These tests
 * pin the answers to real durable state, because an answer that is merely
 * plausible is worse than no answer at all.
 */
class HomeStateProviderTest {
    private fun provider(
        repoRoot: Path,
        queueReadable: (Path) -> Boolean = { true }
    ): HomeStateProvider =
        HomeStateProvider(
            repoRoot = repoRoot,
            queueStore = AgentQueueStore(repoRoot),
            queueEntriesDir = repoRoot.resolve(".atropos/agent/queue").resolve("entries"),
            projectRegistry = atropos.core.project.ProjectRegistry(repoRoot),
            // The git probe shells out and is bounded by a timeout; the six
            // answers must not depend on it.
            workspaceInspector = WorkspaceInspector { RepositoryState.unknown() },
            queueReadable = queueReadable
        )

    @Test
    fun empty_queue_reports_idle_and_offers_an_actionable_next_step() {
        val repoRoot = Files.createTempDirectory("atropos-home-empty-")
        val state = provider(repoRoot).capture(activeProvider = "groq")

        assertTrue(state.queueReadable, "a workspace with no queue yet is readable, not faulted")
        assertEquals("no objective queued", state.answers.objective.value)
        assertEquals(Health.UNKNOWN, state.answers.objective.health)
        // "What should I do next?" must be a command, not a description.
        assertTrue(
            state.answers.next.value.startsWith("/agent queue add"),
            "next action must be typeable: ${state.answers.next.value}"
        )
    }

    @Test
    fun running_entry_answers_objective_doing_why_and_evidence_from_the_record() {
        val repoRoot = Files.createTempDirectory("atropos-home-running-")
        val store = AgentQueueStore(repoRoot)
        val created = store.createEntry(task = "wire the home cockpit", smokeCommand = null)
        store.update(
            created.copy(
                state = AgentQueueState.RUNNING,
                checkpoint = AgentQueueCheckpoint.PATCH_APPLIED,
                sourceEvidence = "97cff09c [S0013] lines 46-48"
            )
        )

        val state = provider(repoRoot).capture(activeProvider = "groq")

        assertEquals("wire the home cockpit", state.answers.objective.value)
        assertTrue(state.answers.doing.value.startsWith("running "), state.answers.doing.value)
        assertTrue(state.answers.why.value.contains("[S0013]"), state.answers.why.value)
        assertTrue(state.answers.progress.value.contains("patch applied"), state.answers.progress.value)
        assertTrue(state.answers.evidence.value.startsWith("1 linked"), state.answers.evidence.value)
        assertEquals(RunState.RUNNING, state.runningWork.single().state)
    }

    @Test
    fun missing_rationale_is_reported_as_missing_rather_than_invented() {
        val repoRoot = Files.createTempDirectory("atropos-home-why-")
        val store = AgentQueueStore(repoRoot)
        val created = store.createEntry(task = "no rationale here", smokeCommand = null)
        store.update(created.copy(state = AgentQueueState.RUNNING, provider = "groq"))

        val why = provider(repoRoot).capture(activeProvider = "groq").answers.why

        assertTrue(why.value.startsWith("no rationale recorded"), why.value)
        assertEquals(Health.PENDING, why.health)
    }

    @Test
    fun failure_drives_the_next_action_to_the_repair_command() {
        val repoRoot = Files.createTempDirectory("atropos-home-failed-")
        val store = AgentQueueStore(repoRoot)
        val created = store.createEntry(task = "broken task", smokeCommand = null)
        store.update(
            created.copy(
                state = AgentQueueState.FAILED,
                checkpoint = AgentQueueCheckpoint.FINALIZED,
                failureReason = "smoke failed"
            )
        )

        val state = provider(repoRoot).capture(activeProvider = "groq")

        assertEquals(1, state.failedItems)
        assertEquals(Health.ERROR, state.answers.next.health)
        assertTrue(
            state.answers.next.value.contains(created.id),
            "the repair command must name the failing entry: ${state.answers.next.value}"
        )
        assertEquals(Health.ERROR, state.answers.progress.health)
    }

    @Test
    fun unreadable_queue_is_never_reported_as_an_idle_queue() {
        val repoRoot = Files.createTempDirectory("atropos-home-unreadable-")
        // A queue directory that exists but cannot be read is a fault, not calm.
        Files.createDirectories(repoRoot.resolve(".atropos/agent/queue/entries"))

        val state = provider(repoRoot, queueReadable = { false })
            .capture(activeProvider = "groq")

        assertFalse(state.queueReadable)
        assertEquals(Health.ERROR, state.answers.objective.health)
        assertEquals(Health.ERROR, state.answers.next.health)
        assertTrue(state.answers.objective.value.startsWith("unreadable"), state.answers.objective.value)
    }

    @Test
    fun queue_task_text_is_redacted_before_it_reaches_the_cockpit() {
        val repoRoot = Files.createTempDirectory("atropos-home-secret-")
        val store = AgentQueueStore(repoRoot)
        val created = store.createEntry(
            task = "deploy with sk-ant-api03-SUPERSECRETVALUE0123456789",
            smokeCommand = null
        )
        store.update(created.copy(state = AgentQueueState.RUNNING))

        val objective = provider(repoRoot).capture(activeProvider = "groq").answers.objective.value

        assertFalse(
            objective.contains("SUPERSECRETVALUE"),
            "§13: secrets never appear in ordinary views, got: $objective"
        )
    }

    // ---- CLI-010: the project section on Home -------------------------------

    @Test
    fun a_stated_project_objective_outranks_the_queue_task() {
        val repoRoot = Files.createTempDirectory("atropos-home-project-objective-")
        val registry = atropos.core.project.ProjectRegistry(repoRoot)
        registry.register(name = "cascade", objective = "retire the legacy router")

        // A running queue entry exists too; §3.2 puts the objective first.
        val store = AgentQueueStore(repoRoot)
        val entry = store.createEntry(task = "apply patch 3 of 9", smokeCommand = null)
        store.update(entry.copy(state = AgentQueueState.RUNNING))

        val state = provider(repoRoot).capture(activeProvider = "groq")

        assertEquals("retire the legacy router", state.answers.objective.value)
        assertEquals(1, state.projects.size)
        assertTrue(state.projectsReadable)
    }

    @Test
    fun a_finished_project_is_not_offered_as_the_current_objective() {
        val repoRoot = Files.createTempDirectory("atropos-home-project-terminal-")
        val registry = atropos.core.project.ProjectRegistry(repoRoot)
        val project = registry.register(name = "done", objective = "already achieved").record
        registry.setStatus(project, atropos.core.project.ProjectStatus.COMPLETED)

        val state = provider(repoRoot).capture(activeProvider = "groq")

        // The objective was stated, but it is not what the operator is
        // currently trying to accomplish.
        assertTrue(state.answers.objective.value.startsWith("no active project"), state.answers.objective.value)
        assertEquals(1, state.projects.size)
    }

    @Test
    fun a_completion_without_evidence_is_carried_to_the_cockpit() {
        val repoRoot = Files.createTempDirectory("atropos-home-project-unverified-")
        val registry = atropos.core.project.ProjectRegistry(repoRoot)
        val project = registry.register(name = "claims done").record
        registry.setStatus(project, atropos.core.project.ProjectStatus.COMPLETED)

        val summary = provider(repoRoot).capture(activeProvider = "groq").projects.single()

        // §3.4 travels with the record rather than being re-derived by the view.
        assertTrue(!summary.completionIsVerifiable)
        assertEquals("completed", summary.statusLabel)
    }
}
