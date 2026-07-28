package atropos.core.agent

import atropos.core.AtroposConfig
import atropos.core.ApiKeys
import atropos.core.dag.DagExecutionService
import atropos.core.dag.DagNode
import atropos.core.dag.DagNodeAction
import atropos.core.dag.DagNodeState
import atropos.core.LakehouseConfig
import atropos.core.RuntimeConfig
import atropos.core.memory.MemoryKind
import java.nio.file.Files
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SelfHostGoalServiceTest {
    private fun initializeGitRepo(repoRoot: java.nio.file.Path) {
        ProcessBuilder("git", "init")
            .directory(repoRoot.toFile())
            .redirectErrorStream(true)
            .start()
            .waitFor()
        Files.createDirectories(repoRoot.resolve("src/main/kotlin/atropos"))
        Files.createDirectories(repoRoot.resolve("src/test/kotlin/atropos"))
        Files.writeString(repoRoot.resolve("src/main/kotlin/atropos/Main.kt"), "fun main() {}\n")
    }

    @Test
    fun resumeGoal_routes_self_host_recovery_through_continuation_service() {
        val repoRoot = Files.createTempDirectory("atropos-self-host-resume-")
        val now = Instant.parse("2026-07-27T07:00:00Z")
        val store = GoalRunStore(repoRoot, clock = { now })
        val continuationService = GoalContinuationService(repoRoot = repoRoot, store = store, clock = { now })
        val service = SelfHostGoalService(
            repoRoot = repoRoot,
            store = store,
            continuationService = continuationService,
            clock = { now }
        )

        val created = store.createGoalRun("phase 11 self-host resume", provider = "self-host")
        val recovered = store.update(
            created.copy(
                goalId = created.id,
                status = GoalRunStatus.RECOVERY_REQUIRED,
                continuationCount = 1,
                activePhase = "11",
                currentNodeId = "node-10",
                lastVerifiedCheckpoint = "verify-6",
                failureReason = "interrupted: recovered during crash recovery",
                evidence = listOf("recovery=crash")
            )
        )

        val result = service.resumeGoal(recovered.id, compactState = "self-host resume")

        assertTrue(result.ok)
        val resumed = result.goal?.record ?: error("missing resumed goal")
        assertEquals(GoalRunStatus.CONTINUING, resumed.status)
        assertEquals(2, resumed.continuationCount)
        assertNull(resumed.failureReason)
        assertTrue(resumed.evidence.any { it == "recovery=crash" })
        assertTrue(resumed.evidence.any { it.contains("recovery_resumed_at=$now") && it.contains("node=node-10") })
    }

    @Test
    fun startGoal_creates_a_durable_cradle_bootstrap_dag() {
        val repoRoot = Files.createTempDirectory("atropos-self-host-start-dag-")
        initializeGitRepo(repoRoot)
        val base = Instant.parse("2026-07-27T07:03:00Z")
        var tick = 0L
        val store = GoalRunStore(repoRoot, clock = { base.plusSeconds(tick++) })
        val service = SelfHostGoalService(repoRoot = repoRoot, store = store, clock = { base.plusSeconds(tick++) })

        val result = service.startGoal("build ATROPOS from natural language", "11")

        assertTrue(result.ok)
        val goal = result.goal ?: error("missing started goal")
        val dag = goal.dag ?: error("missing bootstrap DAG")
        assertEquals(dag.id, goal.record.dagId)
        assertEquals(dag.id, store.resolve(goal.record.id)?.dagId)
        assertEquals(listOf("src/main/kotlin/atropos", "src/test/kotlin/atropos"), goal.record.territory)
        assertEquals(1, dag.nodes.size)
        assertEquals(DagNodeAction.VERIFY, dag.nodes.single().action)
        assertTrue(dag.nodes.single().actionPayload.orEmpty().contains("git status --short"))
    }

    @Test
    fun advanceGoal_runs_one_attested_verification_node_and_records_completion_evidence() {
        val repoRoot = Files.createTempDirectory("atropos-self-host-advance-")
        initializeGitRepo(repoRoot)
        val base = Instant.parse("2026-07-27T07:04:00Z")
        var tick = 0L
        val store = GoalRunStore(repoRoot, clock = { base.plusSeconds(tick++) })
        val service = SelfHostGoalService(repoRoot = repoRoot, store = store, clock = { base.plusSeconds(tick++) })

        val started = service.startGoal("advance one bounded cradle node", "11")
        val goalId = started.goal?.record?.id ?: error("missing goal id")
        val result = service.advanceGoal(goalId)

        assertTrue(result.ok, result.message)
        val reopened = store.resolve(goalId) ?: error("missing advanced goal")
        assertEquals(GoalRunStatus.COMPLETED, reopened.status)
        assertEquals(GoalTerminalCondition.VERIFIED_COMPLETE, reopened.terminalCondition)
        assertTrue(reopened.evidence.any { it.startsWith("context_attestation system=ATROPOS") })
        assertEquals(started.goal?.record?.currentNodeId, reopened.currentNodeId)

        val dagId = reopened.dagId ?: error("missing DAG id")
        val dag = DagExecutionService(repoRoot = repoRoot).readDag(dagId) ?: error("missing DAG")
        assertEquals(DagNodeState.COMPLETE, dag.nodes.single().state)
        assertTrue(dag.nodes.single().result != null)

        val learned = service.learned(10)
        assertTrue(learned.any { it.subjectType == "selfhost_dag_eval" && it.body.contains("attestation:") })
    }

    @Test
    fun advanceNextResumableGoal_prefers_recovery_required_self_host_work() {
        val repoRoot = Files.createTempDirectory("atropos-self-host-auto-advance-")
        initializeGitRepo(repoRoot)
        val base = Instant.parse("2026-07-27T07:06:00Z")
        var tick = 0L
        val store = GoalRunStore(repoRoot, clock = { base.plusSeconds(tick++) })
        val service = SelfHostGoalService(repoRoot = repoRoot, store = store, clock = { base.plusSeconds(tick++) })

        val running = service.startGoal("ordinary running self-host goal", "11").goal?.record
            ?: error("missing running goal")
        val recovery = service.startGoal("recovered self-host goal", "11").goal?.record
            ?: error("missing recovery goal")
        store.update(
            recovery.copy(
                status = GoalRunStatus.RECOVERY_REQUIRED,
                failureReason = "interrupted"
            )
        )

        val result = service.advanceNextResumableGoal()

        assertTrue(result.ok, result.message)
        assertEquals(recovery.id, result.goal?.record?.id)
        assertEquals(GoalRunStatus.RUNNING, store.resolve(running.id)?.status)
        assertEquals(GoalRunStatus.COMPLETED, store.resolve(recovery.id)?.status)
        assertTrue(store.resolve(recovery.id)?.evidence.orEmpty().any { it.startsWith("context_attestation system=ATROPOS") })
    }

    @Test
    fun resolveResumableGoal_prefers_recovery_required_and_honors_explicit_id() {
        val repoRoot = Files.createTempDirectory("atropos-self-host-select-")
        val base = Instant.parse("2026-07-27T07:10:00Z")
        var tick = 0L
        val store = GoalRunStore(repoRoot, clock = { base.plusSeconds(tick++) })
        val service = SelfHostGoalService(repoRoot = repoRoot, store = store)

        val running = store.createGoalRun("running goal", provider = "self-host")
        val recovered = store.createGoalRun("recovery goal", provider = "self-host")
        store.update(running.copy(status = GoalRunStatus.RUNNING, activePhase = "11"))
        store.update(recovered.copy(status = GoalRunStatus.RECOVERY_REQUIRED, activePhase = "11"))

        val preferred = service.resolveResumableGoal()
        assertTrue(preferred.ok)
        assertEquals(recovered.id, preferred.goal?.record?.id)

        val explicit = service.resolveResumableGoal(running.id)
        assertTrue(explicit.ok)
        assertEquals(running.id, explicit.goal?.record?.id)
    }

    @Test
    fun loadUnfinishedGoals_and_default_status_prefer_recovery_required_runs() {
        val repoRoot = Files.createTempDirectory("atropos-self-host-order-")
        val base = Instant.parse("2026-07-27T07:15:00Z")
        var tick = 0L
        val store = GoalRunStore(repoRoot, clock = { base.plusSeconds(tick++) })
        val service = SelfHostGoalService(repoRoot = repoRoot, store = store)

        val running = store.createGoalRun("running goal", provider = "self-host")
        val recovered = store.createGoalRun("recovery goal", provider = "self-host")
        store.update(running.copy(status = GoalRunStatus.RUNNING, activePhase = "11"))
        store.update(recovered.copy(status = GoalRunStatus.RECOVERY_REQUIRED, activePhase = "11"))

        val ordered = service.loadUnfinishedGoals()
        assertEquals(listOf(recovered.id, running.id), ordered.map { it.record.id })

        val status = service.status()
        assertEquals(recovered.id, status.goalId)
        assertEquals(GoalRunStatus.RECOVERY_REQUIRED, status.status)
    }

    @Test
    fun resolveWatchAndStopGoals_share_canonical_self_host_selection_rules() {
        val repoRoot = Files.createTempDirectory("atropos-self-host-watch-stop-")
        val base = Instant.parse("2026-07-27T07:20:00Z")
        var tick = 0L
        val store = GoalRunStore(repoRoot, clock = { base.plusSeconds(tick++) })
        val service = SelfHostGoalService(repoRoot = repoRoot, store = store)

        val running = store.createGoalRun("running goal", provider = "self-host")
        val recovered = store.createGoalRun("recovery goal", provider = "self-host")
        val terminal = store.createGoalRun("done goal", provider = "self-host")
        store.update(running.copy(status = GoalRunStatus.RUNNING, activePhase = "11"))
        store.update(recovered.copy(status = GoalRunStatus.RECOVERY_REQUIRED, activePhase = "11"))
        store.update(
            terminal.copy(
                status = GoalRunStatus.COMPLETED,
                terminalCondition = GoalTerminalCondition.VERIFIED_COMPLETE,
                activePhase = "11"
            )
        )

        val watchDefault = service.resolveWatchGoal()
        assertTrue(watchDefault.ok)
        assertEquals(recovered.id, watchDefault.goal?.record?.id)

        val watchExplicitTerminal = service.resolveWatchGoal(terminal.id)
        assertTrue(watchExplicitTerminal.ok)
        assertEquals(terminal.id, watchExplicitTerminal.goal?.record?.id)

        val stopDefault = service.resolveStoppableGoal()
        assertTrue(stopDefault.ok)
        assertEquals(recovered.id, stopDefault.goal?.record?.id)

        val stopExplicitTerminal = service.resolveStoppableGoal(terminal.id)
        assertTrue(!stopExplicitTerminal.ok)
        assertEquals("goal already terminal: VERIFIED_COMPLETE", stopExplicitTerminal.message)
    }

    @Test
    fun resolveStatusGoal_and_status_stay_inside_self_host_runs() {
        val repoRoot = Files.createTempDirectory("atropos-self-host-status-")
        val base = Instant.parse("2026-07-27T07:25:00Z")
        var tick = 0L
        val store = GoalRunStore(repoRoot, clock = { base.plusSeconds(tick++) })
        val service = SelfHostGoalService(repoRoot = repoRoot, store = store)

        val generic = store.createGoalRun("generic run", provider = "codex")
        val terminal = store.createGoalRun("done goal", provider = "self-host")
        store.update(
            terminal.copy(
                status = GoalRunStatus.COMPLETED,
                terminalCondition = GoalTerminalCondition.VERIFIED_COMPLETE,
                activePhase = "11"
            )
        )

        val selected = service.resolveStatusGoal()
        assertTrue(selected.ok)
        assertEquals(terminal.id, selected.goal?.record?.id)

        val explicitNonSelfHost = service.resolveStatusGoal(generic.id)
        assertTrue(!explicitNonSelfHost.ok)
        assertEquals("goal is not self-host managed: ${generic.id}", explicitNonSelfHost.message)

        val status = service.status(generic.id)
        assertEquals(generic.id, status.goalId)
        assertEquals(GoalRunStatus.FAILED, status.status)
        assertEquals("goal is not self-host managed: ${generic.id}", status.message)
    }

    @Test
    fun history_and_default_status_do_not_drop_self_host_runs_behind_newer_generic_runs() {
        val repoRoot = Files.createTempDirectory("atropos-self-host-history-")
        val base = Instant.parse("2026-07-27T07:30:00Z")
        var tick = 0L
        val store = GoalRunStore(repoRoot, clock = { base.plusSeconds(tick++) })
        val service = SelfHostGoalService(repoRoot = repoRoot, store = store)

        val selfHost = store.createGoalRun("self-host goal", provider = "self-host")
        store.update(selfHost.copy(status = GoalRunStatus.RECOVERY_REQUIRED, activePhase = "11"))
        repeat(60) { index ->
            store.createGoalRun("generic run $index", provider = "codex")
        }

        val history = service.history(5)
        assertEquals(listOf(selfHost.id), history.map { it.id })

        val status = service.status()
        assertEquals(selfHost.id, status.goalId)
        assertEquals(GoalRunStatus.RECOVERY_REQUIRED, status.status)
    }

    @Test
    fun benchmarkHistory_counts_full_self_host_run_set_beyond_history_window() {
        val repoRoot = Files.createTempDirectory("atropos-self-host-benchmark-")
        val base = Instant.parse("2026-07-27T07:35:00Z")
        var tick = 0L
        val store = GoalRunStore(repoRoot, clock = { base.plusSeconds(tick++) })
        val service = SelfHostGoalService(repoRoot = repoRoot, store = store)

        repeat(55) { index ->
            val created = store.createGoalRun("completed self-host $index", provider = "self-host")
            store.update(
                created.copy(
                    status = GoalRunStatus.COMPLETED,
                    terminalCondition = GoalTerminalCondition.VERIFIED_COMPLETE,
                    continuationCount = 1
                )
            )
        }
        repeat(5) { index ->
            val created = store.createGoalRun("failed self-host $index", provider = "self-host")
            store.update(
                created.copy(
                    status = GoalRunStatus.FAILED,
                    terminalCondition = GoalTerminalCondition.TERMINAL_FAILURE,
                    continuationCount = 2
                )
            )
        }
        repeat(12) { index ->
            store.createGoalRun("generic run $index", provider = "codex")
        }

        val historyWindow = service.history(50)
        assertEquals(50, historyWindow.size)

        val benchmark = service.benchmarkHistory()
        assertEquals(60, benchmark.size)
        assertEquals(55, benchmark.count { it.terminalCondition == GoalTerminalCondition.VERIFIED_COMPLETE })
        assertEquals(5, benchmark.count { it.terminalCondition == GoalTerminalCondition.TERMINAL_FAILURE })
        assertEquals(65, benchmark.sumOf { it.continuationCount })
    }

    @Test
    fun learned_returns_actual_self_host_memory_records_instead_of_missing_subject_bucket() {
        val repoRoot = Files.createTempDirectory("atropos-self-host-learned-")
        val base = Instant.parse("2026-07-27T07:40:00Z")
        var tick = 0L
        val service = SelfHostGoalService(
            repoRoot = repoRoot,
            store = GoalRunStore(repoRoot, clock = { base.plusSeconds(tick++) }),
            clock = { base.plusSeconds(tick++) }
        )

        service.startGoal("self-host goal", "11")

        val memoryStore = atropos.core.memory.LocalMemoryStore(repoRoot.resolve(".atropos/memory").toFile())
        memoryStore.rememberDetailed(
            kind = MemoryKind.BATCH,
            title = "self-host DAG evaluation: 11",
            body = "goal: goal-1",
            tags = listOf("selfhost", "dag", "evaluation"),
            subjectType = "selfhost_dag_eval",
            subjectId = "goal-1"
        )
        memoryStore.rememberDetailed(
            kind = MemoryKind.SESSION,
            title = "unrelated memory",
            body = "ignore me",
            tags = listOf("other"),
            subjectType = "session",
            subjectId = "other-1"
        )

        val learned = service.learned(10)

        assertEquals(2, learned.size)
        assertTrue(learned.all { it.subjectType in setOf("selfhost_goal", "selfhost_dag_eval") })
    }

    @Test
    fun selectNextDagNode_returns_terminal_record_when_terminal_dag_has_no_ready_nodes() {
        val repoRoot = Files.createTempDirectory("atropos-self-host-terminal-select-")
        val base = Instant.parse("2026-07-27T07:42:00Z")
        var tick = 0L
        val store = GoalRunStore(repoRoot, clock = { base.plusSeconds(tick++) })
        val service = SelfHostGoalService(repoRoot = repoRoot, store = store, clock = { base.plusSeconds(tick++) })
        val config = AtroposConfig(
            ApiKeys("", "", "", ""),
            LakehouseConfig(repoRoot.resolve("lakehouse").toString(), repoRoot.resolve("lakehouse/vector_storage.db").toString()),
            RuntimeConfig("groq", 0.2)
        )
        val dagService = DagExecutionService(config = config, repoRoot = repoRoot)

        val started = service.startGoal("terminal self-host goal", "11")
        val goalId = started.goal?.record?.id ?: error("missing goal id")
        val dag = dagService.createDag(
            label = "terminal dag",
            nodes = listOf(
                DagNode(
                    id = "node-complete",
                    label = "done",
                    action = DagNodeAction.VERIFY,
                    state = DagNodeState.COMPLETE,
                    createdAt = base,
                    updatedAt = base,
                    metaFile = repoRoot.resolve("unused-node.meta")
                )
            )
        )
        service.setDag(goalId, dag.id)

        val result = service.selectNextDagNode(goalId)

        assertTrue(!result.ok)
        assertEquals("no ready nodes in DAG ${dag.id}", result.message)
        assertEquals(GoalRunStatus.COMPLETED, result.goal?.record?.status)
        assertEquals(GoalTerminalCondition.VERIFIED_COMPLETE, result.goal?.record?.terminalCondition)
    }

    @Test
    fun benchmark_status_stays_partial_when_completion_coexists_with_failures_or_recovery_debt() {
        val repoRoot = Files.createTempDirectory("atropos-self-host-benchmark-status-")
        val base = Instant.parse("2026-07-27T07:45:00Z")
        var tick = 0L
        val store = GoalRunStore(repoRoot, clock = { base.plusSeconds(tick++) })
        val service = SelfHostGoalService(repoRoot = repoRoot, store = store)

        val completed = store.createGoalRun("completed goal", provider = "self-host")
        store.update(
            completed.copy(
                status = GoalRunStatus.COMPLETED,
                terminalCondition = GoalTerminalCondition.VERIFIED_COMPLETE,
                continuationCount = 1
            )
        )
        val failed = store.createGoalRun("failed goal", provider = "self-host")
        store.update(
            failed.copy(
                status = GoalRunStatus.FAILED,
                terminalCondition = GoalTerminalCondition.TERMINAL_FAILURE,
                continuationCount = 2
            )
        )
        val recovering = store.createGoalRun("recovering goal", provider = "self-host")
        store.update(
            recovering.copy(
                status = GoalRunStatus.RECOVERY_REQUIRED,
                continuationCount = 3
            )
        )

        val benchmark = service.benchmark()
        assertEquals(3, benchmark.totalGoals)
        assertEquals(1, benchmark.completed)
        assertEquals(1, benchmark.failed)
        assertEquals(1, benchmark.recoveryRequired)
        assertEquals(6, benchmark.totalContinuations)
        assertEquals("PARTIAL_EVIDENCE", benchmark.status)
    }
}
