package atropos.cli.commands

import atropos.cli.config.ConfigurationManager
import atropos.cli.ui.AnsiTerminalEngine
import atropos.core.ApiKeys
import atropos.core.AtroposConfig
import atropos.core.LakehouseConfig
import atropos.core.RuntimeConfig
import atropos.core.dag.DagExecutionService
import atropos.core.dag.DagNode
import atropos.core.dag.DagNodeAction
import atropos.core.dag.DagNodeState
import atropos.core.agent.GoalRunStatus
import atropos.core.agent.GoalRunStore
import atropos.core.agent.GoalTerminalCondition
import atropos.core.agent.SelfHostAutonomousRunResult
import atropos.core.agent.SelfHostGoalService
import atropos.core.journal.EventCategory
import atropos.core.journal.EventJournalService
import java.io.OutputStream
import java.io.PrintStream
import java.nio.file.Files
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SelfHostCommandTest {
    private fun initializeGitRepo(repoRoot: java.nio.file.Path) {
        ProcessBuilder("git", "init")
            .directory(repoRoot.toFile())
            .redirectErrorStream(true)
            .start()
            .waitFor()
        Files.createDirectories(repoRoot.resolve("src/main/kotlin/atropos"))
        Files.createDirectories(repoRoot.resolve("src/test/kotlin/atropos"))
        Files.writeString(repoRoot.resolve("src/main/kotlin/atropos/Main.kt"), "fun main() {}\n")
        ProcessBuilder("git", "config", "user.email", "atropos@example.invalid")
            .directory(repoRoot.toFile())
            .redirectErrorStream(true)
            .start()
            .waitFor()
        ProcessBuilder("git", "config", "user.name", "ATROPOS Test")
            .directory(repoRoot.toFile())
            .redirectErrorStream(true)
            .start()
            .waitFor()
        ProcessBuilder("git", "add", ".")
            .directory(repoRoot.toFile())
            .redirectErrorStream(true)
            .start()
            .waitFor()
        ProcessBuilder("git", "commit", "-m", "initial")
            .directory(repoRoot.toFile())
            .redirectErrorStream(true)
            .start()
            .waitFor()
    }

    private fun buildCommand(
        repoRoot: java.nio.file.Path,
        service: SelfHostGoalService,
        selfHostRunner: ((String) -> SelfHostAutonomousRunResult)? = null
    ): SelfHostCommand {
        val ui = AnsiTerminalEngine(
            capabilities = ConfigurationManager(),
            out = PrintStream(OutputStream.nullOutputStream()),
            errors = PrintStream(OutputStream.nullOutputStream())
        )
        val config = AtroposConfig(
            ApiKeys("", "", "", ""),
            LakehouseConfig(repoRoot.resolve("lakehouse").toString(), repoRoot.resolve("lakehouse/vector_storage.db").toString()),
            RuntimeConfig("groq", 0.2)
        )
        return SelfHostCommand(
            ui = ui,
            config = config,
            repoRoot = repoRoot,
            selfHostService = service,
            selfHostRunner = selfHostRunner ?: service::runNaturalLanguageSelfBuild
        )
    }

    @Test
    fun `self-host benchmark reports batch evidence status instead of crossover status`() {
        val repoRoot = Files.createTempDirectory("atropos-self-host-command-benchmark-")
        val base = Instant.parse("2026-07-27T07:55:00Z")
        var tick = 0L
        val store = GoalRunStore(repoRoot, clock = { base.plusSeconds(tick++) })
        val service = SelfHostGoalService(repoRoot = repoRoot, store = store)

        val completed = store.createGoalRun("completed goal", provider = "self-host")
        store.update(
            completed.copy(
                status = GoalRunStatus.COMPLETED,
                terminalCondition = GoalTerminalCondition.VERIFIED_COMPLETE
            )
        )
        val failed = store.createGoalRun("failed goal", provider = "self-host")
        store.update(
            failed.copy(
                status = GoalRunStatus.FAILED,
                terminalCondition = GoalTerminalCondition.TERMINAL_FAILURE
            )
        )

        val command = buildCommand(repoRoot, service)

        val result = command.execute(listOf("self-host", "benchmark"))
        val text = when (result) {
            is AgentCommandOutcome.Completed -> result.text
            is AgentCommandOutcome.Invalid -> result.message
        }

        assertTrue(text.contains("batch evidence status: PARTIAL_EVIDENCE"))
        assertTrue(!text.contains("crossover status:"))
    }

    @Test
    fun `self-host start then resume advances the cradle bootstrap DAG`() {
        val repoRoot = Files.createTempDirectory("atropos-self-host-command-cradle-")
        initializeGitRepo(repoRoot)
        val base = Instant.parse("2026-07-27T08:00:00Z")
        var tick = 0L
        val store = GoalRunStore(repoRoot, clock = { base.plusSeconds(tick++) })
        val service = SelfHostGoalService(repoRoot = repoRoot, store = store, clock = { base.plusSeconds(tick++) })
        val command = buildCommand(repoRoot, service)

        val started = command.execute(listOf("/agent", "self-host", "start", "prove", "cradle", "self-build", "--phase", "11"))
        assertTrue(started is AgentCommandOutcome.Completed)
        val goal = service.history(1).single()

        val firstResume = command.execute(listOf("/agent", "self-host", "resume", goal.id))
        val firstText = when (firstResume) {
            is AgentCommandOutcome.Completed -> firstResume.text
            is AgentCommandOutcome.Invalid -> firstResume.message
        }
        assertTrue(firstResume is AgentCommandOutcome.Completed, firstText)
        assertTrue(firstText.contains("advance: DAG node evaluation:"), firstText)

        val secondResume = command.execute(listOf("/agent", "self-host", "resume", goal.id))
        val secondText = when (secondResume) {
            is AgentCommandOutcome.Completed -> secondResume.text
            is AgentCommandOutcome.Invalid -> secondResume.message
        }
        assertTrue(secondResume is AgentCommandOutcome.Completed, secondText)

        val resumed = command.execute(listOf("/agent", "self-host", "resume", goal.id))
        val text = when (resumed) {
            is AgentCommandOutcome.Completed -> resumed.text
            is AgentCommandOutcome.Invalid -> resumed.message
        }
        val reopened = store.resolve(goal.id) ?: error("missing goal")

        assertTrue(resumed is AgentCommandOutcome.Completed, text)
        assertTrue(text.contains("advance: advanced and completed: all nodes complete"), text)
        assertEquals(GoalRunStatus.COMPLETED, reopened.status)
        assertEquals(GoalTerminalCondition.VERIFIED_COMPLETE, reopened.terminalCondition)
        assertTrue(reopened.evidence.any { it.startsWith("context_attestation system=ATROPOS") })
        val marker = repoRoot.resolve("src/main/kotlin/atropos/core/agent/SelfHostCradleRuntimeState.kt")
        val test = repoRoot.resolve("src/test/kotlin/atropos/core/agent/SelfHostCradleRuntimeStateTest.kt")
        assertTrue(Files.readString(marker).contains("LAST_SELF_HOST_GOAL: String = \"${goal.id}\""))
        assertTrue(Files.readString(test).contains("records_latest_self_host_goal_and_phase"))
    }

    @Test
    fun `self-host watch falls back to canonical status when journal is empty`() {
        val repoRoot = Files.createTempDirectory("atropos-self-host-command-watch-")
        val base = Instant.parse("2026-07-27T08:05:00Z")
        var tick = 0L
        val store = GoalRunStore(repoRoot, clock = { base.plusSeconds(tick++) })
        val service = SelfHostGoalService(repoRoot = repoRoot, store = store, clock = { base.plusSeconds(tick++) })

        val started = service.startGoal("watchable goal", "11")
        val startedGoal = started.goal ?: error("missing started goal")
        val goalId = startedGoal.record.id
        store.update(startedGoal.record.copy(currentNodeId = "node-7"))

        val command = buildCommand(repoRoot, service)
        val result = command.execute(listOf("self-host", "watch", goalId))
        val text = when (result) {
            is AgentCommandOutcome.Completed -> result.text
            is AgentCommandOutcome.Invalid -> result.message
        }

        assertTrue(text.contains("no events for goal $goalId"))
        assertTrue(text.contains("status: RUNNING"))
        assertTrue(text.contains("phase: 11"))
        assertTrue(text.contains("node: node-7"))
    }

    @Test
    fun `self-host status accepts explicit self-host goal id including terminal goals`() {
        val repoRoot = Files.createTempDirectory("atropos-self-host-command-status-explicit-")
        val base = Instant.parse("2026-07-27T08:12:00Z")
        var tick = 0L
        val store = GoalRunStore(repoRoot, clock = { base.plusSeconds(tick++) })
        val service = SelfHostGoalService(repoRoot = repoRoot, store = store, clock = { base.plusSeconds(tick++) })

        val started = service.startGoal("inspectable goal", "11")
        val startedGoal = started.goal ?: error("missing started goal")
        val goalId = startedGoal.record.id
        store.update(
            startedGoal.record.copy(
                status = GoalRunStatus.COMPLETED,
                terminalCondition = GoalTerminalCondition.VERIFIED_COMPLETE,
                currentNodeId = "node-9"
            )
        )

        val command = buildCommand(repoRoot, service)
        val result = command.execute(listOf("self-host", "status", goalId))
        val text = when (result) {
            is AgentCommandOutcome.Completed -> result.text
            is AgentCommandOutcome.Invalid -> result.message
        }

        assertTrue(result is AgentCommandOutcome.Completed)
        assertTrue(text.contains("$goalId: COMPLETED"))
        assertTrue(text.contains("terminal: VERIFIED_COMPLETE"))
        assertTrue(text.contains("node: node-9"))
    }

    @Test
    fun `self-host next reports promotion boundary for verified source goal`() {
        val repoRoot = Files.createTempDirectory("atropos-self-host-command-next-")
        val base = Instant.parse("2026-07-27T08:13:00Z")
        var tick = 0L
        val store = GoalRunStore(repoRoot, clock = { base.plusSeconds(tick++) })
        val service = SelfHostGoalService(repoRoot = repoRoot, store = store, clock = { base.plusSeconds(tick++) })
        val started = service.startGoal("next action goal", "11").goal ?: error("missing goal")
        store.update(
            started.record.copy(
                status = GoalRunStatus.COMPLETED,
                terminalCondition = GoalTerminalCondition.VERIFIED_COMPLETE,
                currentNodeId = "node-promote"
            )
        )

        val command = buildCommand(repoRoot, service)
        val result = command.execute(listOf("self-host", "next", started.record.id))
        val text = when (result) {
            is AgentCommandOutcome.Completed -> result.text
            is AgentCommandOutcome.Invalid -> result.message
        }

        assertTrue(result is AgentCommandOutcome.Completed)
        assertTrue(text.contains("next: PROMOTE_JAR"), text)
        assertTrue(text.contains("goal: ${started.record.id}"), text)
    }

    @Test
    fun `self-host run delegates natural-language prompt to core runner`() {
        val repoRoot = Files.createTempDirectory("atropos-self-host-command-run-")
        val store = GoalRunStore(repoRoot)
        val service = SelfHostGoalService(repoRoot = repoRoot, store = store)
        var capturedPrompt: String? = null
        val command = buildCommand(repoRoot, service) { prompt ->
            capturedPrompt = prompt
            SelfHostAutonomousRunResult(
                ok = true,
                message = "self-host run promoted verified jar",
                goal = null,
                promotion = null,
                evidenceBundle = null,
                steps = listOf("started", "promoted")
            )
        }

        val result = command.execute(listOf("self-host", "run", "make", "ATROPOS", "build", "itself"))
        val text = when (result) {
            is AgentCommandOutcome.Completed -> result.text
            is AgentCommandOutcome.Invalid -> result.message
        }

        assertTrue(result is AgentCommandOutcome.Completed)
        assertEquals("make ATROPOS build itself", capturedPrompt)
        assertTrue(text.contains("self-host run promoted verified jar"), text)
        assertTrue(text.contains("started"), text)
    }

    @Test
    fun `self-host status rejects explicit non self-host goal ids`() {
        val repoRoot = Files.createTempDirectory("atropos-self-host-command-status-generic-")
        val base = Instant.parse("2026-07-27T08:14:00Z")
        var tick = 0L
        val store = GoalRunStore(repoRoot, clock = { base.plusSeconds(tick++) })
        val service = SelfHostGoalService(repoRoot = repoRoot, store = store, clock = { base.plusSeconds(tick++) })

        val generic = store.createGoalRun("generic goal", provider = "groq")
        val command = buildCommand(repoRoot, service)
        val result = command.execute(listOf("self-host", "status", generic.id))
        val text = when (result) {
            is AgentCommandOutcome.Completed -> result.text
            is AgentCommandOutcome.Invalid -> result.message
        }

        assertTrue(result is AgentCommandOutcome.Invalid)
        assertTrue(text.contains("goal is not self-host managed"))
    }

    @Test
    fun `self-host status defaults to canonical self-host selection instead of drifting to newer generic runs`() {
        val repoRoot = Files.createTempDirectory("atropos-self-host-command-status-default-")
        val base = Instant.parse("2026-07-27T08:15:00Z")
        var tick = 0L
        val store = GoalRunStore(repoRoot, clock = { base.plusSeconds(tick++) })
        val service = SelfHostGoalService(repoRoot = repoRoot, store = store, clock = { base.plusSeconds(tick++) })

        val selfHostStarted = service.startGoal("terminal self-host goal", "11")
        val selfHostGoal = selfHostStarted.goal ?: error("missing self-host goal")
        val selfHostGoalId = selfHostGoal.record.id
        store.update(
            selfHostGoal.record.copy(
                status = GoalRunStatus.COMPLETED,
                terminalCondition = GoalTerminalCondition.VERIFIED_COMPLETE,
                currentNodeId = "node-terminal"
            )
        )

        store.createGoalRun("newer generic goal", provider = "groq")

        val command = buildCommand(repoRoot, service)
        val result = command.execute(listOf("self-host", "status"))
        val text = when (result) {
            is AgentCommandOutcome.Completed -> result.text
            is AgentCommandOutcome.Invalid -> result.message
        }

        assertTrue(result is AgentCommandOutcome.Completed)
        assertTrue(text.contains("$selfHostGoalId: COMPLETED"))
        assertTrue(text.contains("terminal: VERIFIED_COMPLETE"))
        assertTrue(text.contains("node: node-terminal"))
        assertTrue(!text.contains("newer generic goal"))
    }

    @Test
    fun `self-host invalid command usage advertises explicit goal id selectors`() {
        val repoRoot = Files.createTempDirectory("atropos-self-host-command-usage-")
        val base = Instant.parse("2026-07-27T08:16:00Z")
        var tick = 0L
        val store = GoalRunStore(repoRoot, clock = { base.plusSeconds(tick++) })
        val service = SelfHostGoalService(repoRoot = repoRoot, store = store, clock = { base.plusSeconds(tick++) })
        val command = buildCommand(repoRoot, service)

        val result = command.execute(listOf("self-host", "nope"))
        val text = when (result) {
            is AgentCommandOutcome.Completed -> result.text
            is AgentCommandOutcome.Invalid -> result.message
        }

        assertTrue(result is AgentCommandOutcome.Invalid)
        assertTrue(text.contains("status [goal-id]"))
        assertTrue(text.contains("watch [goal-id]"))
        assertTrue(text.contains("resume [goal-id]"))
        assertTrue(text.contains("stop [goal-id]"))
        assertTrue(text.contains("verify [goal-id]"))
    }

    @Test
    fun `self-host command accepts agent-prefixed dispatch tokens`() {
        val repoRoot = Files.createTempDirectory("atropos-self-host-command-agent-prefix-")
        val base = Instant.parse("2026-07-27T08:18:00Z")
        var tick = 0L
        val store = GoalRunStore(repoRoot, clock = { base.plusSeconds(tick++) })
        val service = SelfHostGoalService(repoRoot = repoRoot, store = store, clock = { base.plusSeconds(tick++) })
        val command = buildCommand(repoRoot, service)

        val result = command.execute(listOf("/agent", "self-host", "status"))
        val text = when (result) {
            is AgentCommandOutcome.Completed -> result.text
            is AgentCommandOutcome.Invalid -> result.message
        }

        assertTrue(result is AgentCommandOutcome.Completed)
        assertTrue(text.contains("no active self-host goals"))
    }

    @Test
    fun `self-host start and stop record lifecycle provenance in the journal`() {
        val repoRoot = Files.createTempDirectory("atropos-self-host-command-lifecycle-")
        val base = Instant.parse("2026-07-27T08:20:00Z")
        var tick = 0L
        val store = GoalRunStore(repoRoot, clock = { base.plusSeconds(tick++) })
        val service = SelfHostGoalService(repoRoot = repoRoot, store = store, clock = { base.plusSeconds(tick++) })
        val journal = EventJournalService(repoRoot = repoRoot, clock = { base.plusSeconds(tick++) })
        val command = buildCommand(repoRoot, service)

        val started = command.execute(listOf("self-host", "start", "phase", "11", "goal"))
        val startedText = when (started) {
            is AgentCommandOutcome.Completed -> started.text
            is AgentCommandOutcome.Invalid -> started.message
        }
        val goalId = startedText.substringAfterLast(": ").trim()

        val stopResult = command.execute(listOf("self-host", "stop", goalId))
        val stopText = when (stopResult) {
            is AgentCommandOutcome.Completed -> stopResult.text
            is AgentCommandOutcome.Invalid -> stopResult.message
        }

        val events = journal.readEvents(goalId, 10)
        assertEquals(2, events.size)
        assertEquals(EventCategory.LIFECYCLE, events[0].category)
        assertTrue(events[0].payload.contains("started:"))
        assertEquals(EventCategory.CANCELLATION, events[1].category)
        assertTrue(events[1].payload.contains("stopped:"))
        assertTrue(stopText.contains("goal completed: CANCELLED"))
    }

    @Test
    fun `self-host start excludes phase flag from persisted task text`() {
        val repoRoot = Files.createTempDirectory("atropos-self-host-command-start-phase-")
        val base = Instant.parse("2026-07-27T08:25:00Z")
        var tick = 0L
        val store = GoalRunStore(repoRoot, clock = { base.plusSeconds(tick++) })
        val service = SelfHostGoalService(repoRoot = repoRoot, store = store, clock = { base.plusSeconds(tick++) })
        val journal = EventJournalService(repoRoot = repoRoot, clock = { base.plusSeconds(tick++) })
        val command = buildCommand(repoRoot, service)

        val result = command.execute(listOf("self-host", "start", "phase", "11", "goal", "--phase", "7"))
        val text = when (result) {
            is AgentCommandOutcome.Completed -> result.text
            is AgentCommandOutcome.Invalid -> result.message
        }
        val goalId = text.substringAfterLast(": ").trim()

        val reopened = store.resolve(goalId) ?: error("missing started goal")
        val events = journal.readEvents(goalId, 10)

        assertEquals("phase 11 goal", reopened.task)
        assertEquals("7", reopened.activePhase)
        assertTrue(events.first().payload.contains("task=phase 11 goal"))
        assertTrue(!events.first().payload.contains("--phase"))
    }

    @Test
    fun `self-host start defaults natural language goals to phase 11`() {
        val repoRoot = Files.createTempDirectory("atropos-self-host-command-start-default-")
        val base = Instant.parse("2026-07-27T08:26:00Z")
        var tick = 0L
        val store = GoalRunStore(repoRoot, clock = { base.plusSeconds(tick++) })
        val service = SelfHostGoalService(repoRoot = repoRoot, store = store, clock = { base.plusSeconds(tick++) })
        val command = buildCommand(repoRoot, service)

        val result = command.execute(listOf("self-host", "start", "build", "ATROPOS", "from", "inside"))
        val text = when (result) {
            is AgentCommandOutcome.Completed -> result.text
            is AgentCommandOutcome.Invalid -> result.message
        }
        val goalId = text.substringAfterLast(": ").trim()

        val reopened = store.resolve(goalId) ?: error("missing started goal")
        assertEquals("11", reopened.activePhase)
        assertEquals("build ATROPOS from inside", reopened.task)
    }

    @Test
    fun `self-host resume reports verified completion when DAG becomes terminal during selection`() {
        val repoRoot = Files.createTempDirectory("atropos-self-host-command-resume-terminal-")
        val base = Instant.parse("2026-07-27T08:30:00Z")
        var tick = 0L
        val store = GoalRunStore(repoRoot, clock = { base.plusSeconds(tick++) })
        val service = SelfHostGoalService(repoRoot = repoRoot, store = store, clock = { base.plusSeconds(tick++) })
        val journal = EventJournalService(repoRoot = repoRoot, clock = { base.plusSeconds(tick++) })
        val command = buildCommand(repoRoot, service)
        val config = AtroposConfig(
            ApiKeys("", "", "", ""),
            LakehouseConfig(repoRoot.resolve("lakehouse").toString(), repoRoot.resolve("lakehouse/vector_storage.db").toString()),
            RuntimeConfig("groq", 0.2)
        )
        val dagService = DagExecutionService(config = config, repoRoot = repoRoot)

        val started = service.startGoal("resume terminal goal", "11")
        val goalId = started.goal?.record?.id ?: error("missing started goal")
        val dag = dagService.createDag(
            label = "terminal dag",
            nodes = listOf(
                DagNode(
                    id = "node-complete",
                    label = "already complete",
                    action = DagNodeAction.VERIFY,
                    state = DagNodeState.COMPLETE,
                    createdAt = base,
                    updatedAt = base,
                    metaFile = repoRoot.resolve("unused-node.meta")
                )
            )
        )
        service.setDag(goalId, dag.id)

        val result = command.execute(listOf("self-host", "resume", goalId))
        val text = when (result) {
            is AgentCommandOutcome.Completed -> result.text
            is AgentCommandOutcome.Invalid -> result.message
        }
        val reopened = store.resolve(goalId) ?: error("missing resumed goal")
        val events = journal.readEvents(goalId, 10)

        assertTrue(result is AgentCommandOutcome.Completed)
        assertTrue(text.contains("completed: all DAG nodes done"))
        assertEquals(GoalRunStatus.COMPLETED, reopened.status)
        assertEquals(GoalTerminalCondition.VERIFIED_COMPLETE, reopened.terminalCondition)
        assertTrue(events.any { it.category == EventCategory.LIFECYCLE && it.payload.contains("terminal=VERIFIED_COMPLETE") })
    }
}
