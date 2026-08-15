/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.verification

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class GoalRunTest {

    @Test
    fun `instantiates GoalRun fields correctly`() {
        val run = GoalRun("run_1", "goal_1", Instant.EPOCH, "/tmp/worktree", "RUNNING")
        assertEquals("run_1", run.runId)
        assertEquals("/tmp/worktree", run.worktreePath)
    }
}
