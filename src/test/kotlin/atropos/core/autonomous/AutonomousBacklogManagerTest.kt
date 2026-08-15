/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.autonomous

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AutonomousBacklogManagerTest {

    @Test
    fun `manager safely enqueues and claims eligible task`() {
        val tempDir = Files.createTempDirectory("backlog-manager-test-")
        val service = AutonomousBacklogService(tempDir)
        val manager = AutonomousBacklogManager(service)

        val task = manager.enqueueTask(AutonomousTaskKind.REPAIR_RETRY, "fix build")
        assertEquals(AutonomousTaskState.ELIGIBLE, task.state)

        val claimed = manager.claimTask(task.id)
        assertNotNull(claimed)
        assertEquals(AutonomousTaskState.RUNNING, claimed.state)

        manager.completeTask(task.id, "fixed")
        val completed = manager.getTaskDetails(task.id)
        assertNotNull(completed)
        assertEquals(AutonomousTaskState.COMPLETED, completed.state)
        assertEquals("fixed", completed.result)
    }

    @Test
    fun `manager blocks claim when task is pending dependencies`() {
        val tempDir = Files.createTempDirectory("backlog-manager-test-")
        val service = AutonomousBacklogService(tempDir)
        val manager = AutonomousBacklogManager(service)

        val task1 = manager.enqueueTask(AutonomousTaskKind.REPAIR_RETRY, "fix build")
        val task2 = manager.enqueueTask(AutonomousTaskKind.VERIFICATION_GATE, "deploy app", dependencies = listOf(task1.id))

        assertEquals(AutonomousTaskState.PENDING, task2.state)
        assertNull(manager.claimTask(task2.id))

        manager.claimTask(task1.id)
        manager.completeTask(task1.id, "ok")

        // Once task1 completes, task2 should become eligible
        val updatedTask2 = manager.getTaskDetails(task2.id)
        assertNotNull(updatedTask2)
        assertEquals(AutonomousTaskState.ELIGIBLE, updatedTask2.state)
        assertNotNull(manager.claimTask(task2.id))
    }
}
