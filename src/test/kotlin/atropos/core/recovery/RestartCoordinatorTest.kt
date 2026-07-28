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

        assertEquals(goal.id, snapshot.goalRuns.single().id)
        assertEquals(dag.id, snapshot.dags.single().id)
        assertEquals(1, snapshot.dags.single().ready)
        assertEquals(1, snapshot.memoryRecords)
        assertNotNull(latest)
        assertEquals(snapshot.id, latest.id)
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
}
