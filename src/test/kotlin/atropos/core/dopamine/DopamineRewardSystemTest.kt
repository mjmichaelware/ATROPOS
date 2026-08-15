/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.dopamine

import atropos.core.integration.DependencyDeduplicator
import atropos.core.integration.CloudDeploymentGuard
import atropos.core.integration.ShellCommandIntercept
import atropos.core.integration.PipedStreamRouter
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DopamineRewardSystemTest {

    @Test
    fun `RewardCalculator computes ratio value`() {
        val reward = RewardCalculator.computeReward(1.0, 1000.0, 0.05)
        assertEquals(20.0, reward)
    }

    @Test
    fun `RewardPenaltyStore writes logs asynchronously`() {
        val tempLogFile = Files.createTempFile("reward-log-", ".tsv").toFile()
        val store = RewardPenaltyStore(tempLogFile)
        store.recordReward(RewardLogEntry("agent-1", "MUTATE", 10.0, "reasoning"))
        store.shutdown()
        // Wait briefly for asynchronous execution to complete
        Thread.sleep(100)
        val content = tempLogFile.readText()
        assertTrue(content.contains("agent-1\tMUTATE\t10.0\treasoning"))
        tempLogFile.delete()
    }

    @Test
    fun `AlignmentTuner adjusts parameters from rolling average`() {
        val lowHistory = listOf(RewardLogEntry("a", "action", 0.2, "bad"))
        val tunedLow = AlignmentTuner.tune(lowHistory)
        assertEquals(0.2, tunedLow.temperature)

        val highHistory = listOf(RewardLogEntry("a", "action", 0.8, "good"))
        val tunedHigh = AlignmentTuner.tune(highHistory)
        assertEquals(0.7, tunedHigh.temperature)
    }

    @Test
    fun `DependencyDeduplicator removes duplicates`() {
        val list = listOf("depA", "depB", "depA")
        assertEquals(listOf("depA", "depB"), DependencyDeduplicator.deduplicate(list))
    }

    @Test
    fun `CloudDeploymentGuard checks UI-stripped remote endpoints`() {
        assertTrue(CloudDeploymentGuard.isRemoteDeploymentSecure("https://secure.app", true))
        assertFalse(CloudDeploymentGuard.isRemoteDeploymentSecure("http://insecure.app", true))
    }

    @Test
    fun `ShellCommandIntercept rewrites bare commands to slash command`() {
        assertEquals("/git commit", ShellCommandIntercept.intercept("git commit"))
        assertEquals("/cd src", ShellCommandIntercept.intercept("cd src"))
        assertEquals("ls -la", ShellCommandIntercept.intercept("ls -la"))
    }

    @Test
    fun `PipedStreamRouter pipes output data`() {
        val router = PipedStreamRouter()
        val out = router.routePipedCommand("data", "echo", "grep")
        assertTrue(out.contains("grep"))
        assertTrue(out.contains("echo"))
    }
}
