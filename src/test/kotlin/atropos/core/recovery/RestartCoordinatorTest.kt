package atropos.core.recovery

import atropos.core.agent.GoalRunStore
import atropos.core.dag.DagNode
import atropos.core.dag.DagNodeState
import atropos.core.dag.DagStore
import atropos.core.memory.LocalMemoryStore
import atropos.core.worktree.IsolatedWorktreeService
import java.nio.file.Files
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class RestartCoordinatorTest {
    @Test
    fun snapshotCapturesGoalDagWorktreeAndMemoryState() {
        val root = Files.createTempDirectory("atropos-restart-snapshot-")
        val goalStore = GoalRunStore(root, clock = { Instant.parse("2026-07-28T01:00:00Z") })
        val dagStore = DagStore(root)
        val memory = LocalMemoryStore(root.resolve(".atropos/memory").toFile(), env = emptyMap())
        val worktrees = IsolatedWorktreeService(root, memoryStore = memory)
        val goal = goalStore.createGoalRun("self-host restart proof", provider = "self-host")
        val enrichedGoal = goalStore.update(
            goal.copy(
                baselineCommit = "abc123",
                dirtyStateFingerprint = "dirty456",
                parentRunId = "parent-1",
                runId = "run-1",
                territory = listOf("src/with,comma", "src/ordinary"),
                maxContinuations = 12,
                retryBudget = 7,
                lastVerifiedCheckpoint = "source:verified"
            )
        )
        val dag = dagStore.createDag(
            "restart dag",
            listOf(
                DagNode(
                    id = "node-a",
                    label = "ready node",
                    state = DagNodeState.READY,
                    createdAt = Instant.EPOCH,
                    updatedAt = Instant.EPOCH,
                    metaFile = root.resolve("unused")
                )
            )
        )
        memory.rememberVerification("verify-1", "restart evidence", "snapshot evidence", tags = listOf("restart"))

        val coordinator = RestartCoordinator(
            repoRoot = root,
            goalRunStore = goalStore,
            dagStore = dagStore,
            worktreeService = worktrees,
            memoryStore = memory,
            dagNodeRestorer = DagNodeRestorer(dagStore),
            clock = { Instant.parse("2026-07-28T01:02:00Z") }
        )

        val snapshot = coordinator.snapshot()
        val latest = coordinator.latestSnapshot()

        assertEquals(enrichedGoal.id, snapshot.goalRuns.single().id)
        assertEquals(enrichedGoal.task, snapshot.goalRuns.single().task)
        assertEquals(enrichedGoal.baselineCommit, snapshot.goalRuns.single().baselineCommit)
        assertEquals(enrichedGoal.dirtyStateFingerprint, snapshot.goalRuns.single().dirtyStateFingerprint)
        assertEquals(enrichedGoal.parentRunId, snapshot.goalRuns.single().parentRunId)
        assertEquals(enrichedGoal.runId, snapshot.goalRuns.single().runId)
        assertEquals(enrichedGoal.territory, snapshot.goalRuns.single().territory)
        assertEquals(enrichedGoal.maxContinuations, snapshot.goalRuns.single().maxContinuations)
        assertEquals(enrichedGoal.retryBudget, snapshot.goalRuns.single().retryBudget)
        assertEquals(enrichedGoal.lastVerifiedCheckpoint, snapshot.goalRuns.single().lastVerifiedCheckpoint)
        assertEquals(dag.id, snapshot.dags.single().id)
        assertEquals(1, snapshot.dags.single().ready)
        assertEquals("node-a", snapshot.dagNodes.single().nodeId)
        assertEquals("READY", snapshot.dagNodes.single().state)
        assertEquals(1, snapshot.memoryRecords)
        assertNotNull(latest)
        assertEquals(snapshot.id, latest.id)
        assertEquals("node-a", latest.dagNodes.single().nodeId)
        assertEquals(enrichedGoal.territory, latest.goalRuns.single().territory)
        assertEquals(enrichedGoal.baselineCommit, latest.goalRuns.single().baselineCommit)
    }

    @Test
    fun dagNodeRestorerRestoresInterruptedNodesOrBlocksExhaustedNodes() {
        val root = Files.createTempDirectory("atropos-dag-restorer-")
        val dagStore = DagStore(root)
        val dag = dagStore.createDag(
            "restore dag",
            listOf(
                DagNode(
                    id = "node-running",
                    label = "running",
                    state = DagNodeState.READY,
                    maxAttempts = 3,
                    createdAt = Instant.EPOCH,
                    updatedAt = Instant.EPOCH,
                    metaFile = root.resolve("unused-a")
                ),
                DagNode(
                    id = "node-exhausted",
                    label = "exhausted",
                    state = DagNodeState.READY,
                    maxAttempts = 1,
                    createdAt = Instant.EPOCH,
                    updatedAt = Instant.EPOCH,
                    metaFile = root.resolve("unused-b")
                )
            )
        )
        val running = dagStore.readNode("node-running")!!
        val exhausted = dagStore.readNode("node-exhausted")!!
        dagStore.writeNode(running.copy(state = DagNodeState.RUNNING, attempts = 1))
        dagStore.writeNode(exhausted.copy(state = DagNodeState.VERIFYING, attempts = 1))

        val results = DagNodeRestorer(dagStore).restoreInterruptedNodes(dag.id)

        assertTrue(results.any { it.nodeId == "node-running" && it.restored })
        assertTrue(results.any { it.nodeId == "node-exhausted" && !it.restored })
        assertEquals(DagNodeState.READY, dagStore.readNode("node-running")!!.state)
        assertEquals(DagNodeState.BLOCKED, dagStore.readNode("node-exhausted")!!.state)
    }

    @Test
    fun latestSnapshot_restores_full_redacted_recovery_report() {
        val root = Files.createTempDirectory("atropos-restart-report-round-trip-")
        val capturedAt = Instant.parse("2026-07-29T02:00:00Z")
        val recoveredAt = Instant.parse("2026-07-29T01:59:59Z")
        val coordinator = RestartCoordinator(
            repoRoot = root,
            clock = { capturedAt }
        )
        val report = RecoveryReport(
            recoveredAt = recoveredAt,
            staleQueueEntries = 2,
            staleSessions = 3,
            staleDagClaims = 4,
            interruptedRuns = 5,
            completedMutationsSkipped = 6,
            errors = listOf("provider token=plain-token"),
            message = "recovered with token=plain-token"
        )

        coordinator.snapshot(report)

        val restored = coordinator.latestSnapshot()?.recoveryReport
            ?: error("missing recovery report")
        assertEquals(recoveredAt, restored.recoveredAt)
        assertEquals(2, restored.staleQueueEntries)
        assertEquals(3, restored.staleSessions)
        assertEquals(4, restored.staleDagClaims)
        assertEquals(5, restored.interruptedRuns)
        assertEquals(6, restored.completedMutationsSkipped)
        assertEquals(listOf("provider token=<redacted:secret>"), restored.errors)
        assertEquals("recovered with token=<redacted:secret>", restored.message)
    }

    @Test
    fun latestSnapshot_can_select_snapshot_for_requested_goal() {
        val root = Files.createTempDirectory("atropos-restart-goal-scoped-")
        val goalStore = GoalRunStore(root)
        val first = goalStore.createGoalRun("first goal", provider = "self-host")
        val second = goalStore.createGoalRun("second goal", provider = "self-host")
        val worktrees = IsolatedWorktreeService(root)
        val coordinator = RestartCoordinator(
            repoRoot = root,
            goalRunStore = goalStore,
            worktreeService = worktrees,
            clock = { Instant.parse("2026-07-29T02:10:00Z") }
        )

        coordinator.snapshot()
        goalStore.update(second.copy(evidence = listOf("second evidence")))
        coordinator.snapshot()

        assertEquals(second.id, coordinator.latestSnapshot(second.id)?.goalRuns?.single()?.id)
        assertEquals(null, coordinator.latestSnapshot("missing-goal"))
    }
}
