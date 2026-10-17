/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.benchmark

import atropos.core.policy.BoundedProcessResult
import atropos.core.policy.BoundedProcessRunner
import org.junit.Test
import java.io.File
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SweBenchCloudEvaluatorTest {

    @Test
    fun `submitPredictions invokes sb-cli with valid arguments`() {
        val invokedCommands = mutableListOf<List<String>>()

        val dummyRunner = BoundedProcessRunner(
            launcher = { command, dir, _, _ ->
                invokedCommands.add(command)
                ProcessBuilder("true").start()
            }
        )

        val evaluator = SweBenchCloudEvaluator(dummyRunner)
        val tempPredictions = File.createTempFile("preds", ".jsonl")
        tempPredictions.writeText("{\"instance_id\": \"django-123\", \"patch\": \"diff\"}\n")
        tempPredictions.deleteOnExit()

        val exitCode = evaluator.submitPredictions(tempPredictions, "test-run-1", "token-xyz")
        assertEquals(0, exitCode)
        assertTrue(invokedCommands.isNotEmpty())

        val cmd = invokedCommands.first()
        assertEquals("sb-cli", cmd[0])
        assertEquals("submit", cmd[1])
        assertEquals("swe-bench_verified", cmd[2])
        assertEquals("test", cmd[3])
        assertTrue(cmd.contains("--predictions_path"))
        assertTrue(cmd.contains("--run_id"))
        assertTrue(cmd.contains("--token"))
    }
}
