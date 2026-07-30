package atropos.core.agent

import atropos.core.dag.DagExecutionService
import atropos.core.dag.DagNode
import atropos.core.dag.DagNodeAction
import atropos.core.dag.DagNodeState
import atropos.core.recovery.RestartCoordinator
import java.nio.file.Files
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SelfHostRecoveryContinuationTest {
    @Test
    fun recoverAndContinue_restores_interrupted_node_and_advances_it() {
        val root = Files.createTempDirectory("atropos-self-host-recover-continue-")
        initializeGitRepo(root)
        val base = Instant.parse("2026-07-29T00:06:00Z")
        var tick = 0L
        val store = GoalRunStore(root, clock = { base.plusSeconds(tick++) })
        val dagService = DagExecutionService(repoRoot = root)
        val marker = "src/main/kotlin/atropos/core/agent/SelfHostRecoveredMarker.kt"
        val node = DagNode(
            id = "node-recover-edit",
            label = "Recovered self-host source edit",
            territory = listOf("src/main/kotlin/atropos/core/agent"),
            action = DagNodeAction.EDIT_FILE,
            actionPayload = "$marker::package atropos.core.agent\nobject SelfHostRecoveredMarker { const val RESTORED: Boolean = true }",
            expectedOutputs = listOf(marker),
            state = DagNodeState.RUNNING,
            createdAt = base,
            updatedAt = base,
            metaFile = root.resolve(".atropos/dag/node-recover-edit.meta")
        )
        val dag = dagService.createDag("recover self-host dag", listOf(node), "atropos-self-host")
        val goal = store.createGoalRun("recover self-host loop", provider = "self-host")
        val interrupted = store.update(
            goal.copy(
                goalId = goal.id,
                status = GoalRunStatus.RECOVERY_REQUIRED,
                dagId = dag.id,
                activePhase = "11",
                currentNodeId = node.id,
                territory = node.territory,
                evidence = listOf("recovery=crash")
            )
        )
        val service = SelfHostGoalService(
            repoRoot = root,
            store = store,
            dagService = dagService,
            restartCoordinator = RestartCoordinator(root, goalRunStore = store, dagStore = atropos.core.dag.DagStore(root)),
            clock = { base.plusSeconds(tick++) }
        )

        val result = service.recoverAndContinue(interrupted.id)

        assertTrue(result.ok, result.message)
        val reopened = store.resolve(interrupted.id) ?: error("missing goal")
        assertTrue(reopened.evidence.any { it.startsWith("restart_snapshot id=") })
        assertTrue(reopened.evidence.any { it.startsWith("restart_next goal=${interrupted.id}") })
        assertTrue(reopened.evidence.any { it.startsWith("next_action kind=ADVANCE_NODE") })
        assertTrue(reopened.evidence.any { it.startsWith("state_snapshot reason=resume") })
        assertTrue(reopened.evidence.any { it.startsWith("state_snapshot reason=select:node-recover-edit") })
        assertEquals(DagNodeState.COMPLETE, dagService.readDag(dag.id)?.findNode(node.id)?.state)
        assertTrue(Files.readString(root.resolve(marker)).contains("RESTORED: Boolean = true"))
    }

    private fun initializeGitRepo(repoRoot: java.nio.file.Path) {
        ProcessBuilder("git", "init")
            .directory(repoRoot.toFile())
            .redirectErrorStream(true)
            .start()
            .waitFor()
        Files.createDirectories(repoRoot.resolve("src/main/kotlin/atropos/core/agent"))
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
}
