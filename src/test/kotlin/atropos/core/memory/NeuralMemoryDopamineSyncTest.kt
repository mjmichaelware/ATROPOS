/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.memory

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class NeuralMemoryDopamineSyncTest {

    @Test
    fun `records trajectory and queries failure experiences`() {
        val tempDir = Files.createTempDirectory("dopamine-test")
        val sync = NeuralMemoryDopamineSync(tempDir)

        val record = EpisodicMemoryRecord(
            issueId = "astropy__astropy-11693",
            benchmarkName = "swe-bench-pro",
            durationMs = 12000L,
            patchSha256 = "dummy-sha256",
            finalReward = 0.0, // Failed run
            steps = listOf(
                TrajectoryStep(1, 1500, "Inspected wcs.py with ghp_testkey1234567890123456789012345678", 0, 0.0)
            )
        )

        val path = sync.recordTrajectory(record)
        assertTrue(Files.exists(path))

        val failures = sync.querySimilarFailureTrajectories("context length")
        assertEquals(1, failures.size)
        assertEquals("astropy__astropy-11693", failures[0].issueId)
        assertTrue(failures[0].steps[0].actionSummary.contains("[REDACTED_SECRET]"))

        tempDir.toFile().deleteRecursively()
    }
}
