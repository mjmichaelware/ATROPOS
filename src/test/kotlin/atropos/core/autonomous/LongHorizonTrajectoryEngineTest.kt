/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.autonomous

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LongHorizonTrajectoryEngineTest {

    @Test
    fun `evaluates Lyapunov stability and flags monotonic divergence`() {
        val tempDir = Files.createTempDirectory("trajectory-test")
        val engine = LongHorizonTrajectoryEngine(tempDir)

        val node1 = TrajectoryNode(1, "file1.kt", "+ diff1", 100L, null)
        val node2 = TrajectoryNode(2, "file2.kt", "+ diff2", 90L, "sha1")
        val node3 = TrajectoryNode(3, "file3.kt", "+ diff3", 80L, "sha2")

        engine.appendTrajectoryStep(node1)
        engine.appendTrajectoryStep(node2)
        engine.appendTrajectoryStep(node3)

        val stableDecision = engine.evaluateLyapunovConvergence(listOf(node1, node2, node3))
        assertFalse(stableDecision.shouldRollback)

        // Monotonic divergence: potential strictly increasing for 2 consecutive steps
        val divNode1 = TrajectoryNode(1, "file1.kt", "+ diff1", 50L, null)
        val divNode2 = TrajectoryNode(2, "file2.kt", "+ diff2", 70L, "sha1")
        val divNode3 = TrajectoryNode(3, "file3.kt", "+ diff3", 95L, "sha2")

        val divDecision = engine.evaluateLyapunovConvergence(listOf(divNode1, divNode2, divNode3))
        assertTrue(divDecision.shouldRollback)
        assertEquals(1, divDecision.targetStep)

        tempDir.toFile().deleteRecursively()
    }
}
