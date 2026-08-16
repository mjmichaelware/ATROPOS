/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.verification

import java.time.Instant
import java.nio.file.Path
import atropos.core.agent.GoalRunRecord
import atropos.core.agent.GoalRunStatus
import kotlin.test.Test
import kotlin.test.assertEquals

class GoalRunTest {

    @Test
    fun `instantiates GoalRun fields correctly`() {
        val run = GoalRun("run_1", "goal_1", Instant.EPOCH, "/tmp/worktree", "RUNNING")
        assertEquals("run_1", run.runId)
        assertEquals("/tmp/worktree", run.worktreePath)
    }

    @Test
    fun `legacy projection reads canonical goal run without owning persistence`() {
        val record = GoalRunRecord(
            id = "record-1",
            goalId = "goal-1",
            task = "inspect",
            status = GoalRunStatus.COMPLETED,
            createdAt = Instant.EPOCH,
            updatedAt = Instant.EPOCH,
            metaFile = Path.of("/tmp/goal.meta"),
            territory = listOf("/tmp/worktree")
        )
        val projection = GoalRun.from(record)
        assertEquals("record-1", projection.runId)
        assertEquals("goal-1", projection.goalId)
        assertEquals("COMPLETED", projection.status)
    }
}
