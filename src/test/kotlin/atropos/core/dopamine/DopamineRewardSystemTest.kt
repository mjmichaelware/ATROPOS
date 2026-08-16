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
import kotlin.test.assertFailsWith
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
        val content = tempLogFile.readText()
        // The canonical autonomy store owns the row format, and it carries a
        // signal type between the action and the value. This facade only makes
        // the write asynchronous; it does not get its own schema.
        assertTrue(content.contains("agent-1\tMUTATE\tREWARD\t10.0\treasoning"), content)
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

        val prompt = AlignmentTuner.apply("list providers", tunedHigh)
        assertTrue(prompt.startsWith("prefix-standard"))
        assertTrue(prompt.contains("temperature=0.7"))
        assertTrue(prompt.contains("Task:"))
    }

    @Test
    fun `AlignmentTuner uses only bounded recent rewards and emits bounded examples`() {
        val history = (1..30).map {
            RewardLogEntry("a", "old-$it", 0.1, "old")
        } + listOf(
            RewardLogEntry("a", "kept-1", 1.0, "success one"),
            RewardLogEntry("a", "kept-2", 1.0, "success two"),
            RewardLogEntry("a", "kept-3", 1.0, "success three"),
            RewardLogEntry("a", "kept-4", 1.0, "success four")
        )

        val tuned = AlignmentTuner.tune(history, windowSize = 4)

        assertEquals(0.7, tuned.temperature)
        assertEquals(0.95, tuned.topP)
        assertEquals(3, tuned.fewShotExamples.size)
        assertTrue(tuned.fewShotExamples.none { it.contains("old-") })
        assertTrue(tuned.fewShotExamples.all { it.length <= 180 })
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
        val out = router.routePipedCommand("data", "cat", "wc -c")
        assertTrue(out.contains("stage=cat"))
        assertTrue(out.contains("stage=wc"))
        assertTrue(out.contains("output=4"), out)
    }

    @Test
    fun `PipedStreamRouter refuses shell syntax instead of interpreting it`() {
        val router = PipedStreamRouter()
        assertFailsWith<IllegalArgumentException> {
            router.routePipedCommand("data", "cat; rm -rf", "wc -c")
        }
    }
}
