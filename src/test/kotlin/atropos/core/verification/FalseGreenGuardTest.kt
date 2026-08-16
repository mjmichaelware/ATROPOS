/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.verification

import atropos.core.dag.DagNode
import atropos.core.dag.DagNodeState
import atropos.core.dag.DagNodeAction
import java.nio.file.Path
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FalseGreenGuardTest {
    @Test
    fun `guard refuses completion without structural evidence`() {
        val node = DagNode(
            id = "d",
            label = "done",
            action = DagNodeAction.RUN_COMMAND,
            state = DagNodeState.COMPLETE,
            createdAt = Instant.EPOCH,
            updatedAt = Instant.EPOCH,
            metaFile = Path.of("unused")
        )
        assertFalse(FalseGreenGuard().assess(node).passed)
    }

    @Test
    fun `anti oscillation refuses identical outcome after bounded repeats`() {
        val guard = AntiOscillation(2)
        assertTrue(guard.observe("same"))
        assertTrue(guard.observe("same"))
        assertFalse(guard.observe("same"))
        assertEquals(3, guard.count("same"))
    }
}
