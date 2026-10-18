/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.dopamine

import atropos.core.autonomy.RewardPenaltyStore
import atropos.core.provider.ProviderFailureMemoryStore
import java.io.File
import java.nio.file.Files
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProviderDopamineRewardTrackerTest {

    private lateinit var tracker: ProviderDopamineRewardTracker
    private lateinit var tempDir: File

    @BeforeTest
    fun setup() {
        tempDir = Files.createTempDirectory("dopamine-test").toFile()
        val rewardStore = RewardPenaltyStore(
            storageDir = tempDir,
            storageFile = File(tempDir, "rewards.tsv")
        )
        val memoryStore = ProviderFailureMemoryStore()
        tracker = ProviderDopamineRewardTracker(
            rewardStore = rewardStore,
            memoryStore = memoryStore
        )
    }

    @Test
    fun `recordFailure assigns negative reward penalty minus 1`() {
        tracker.recordFailure(
            provider = "groq",
            action = "provider.code_generation",
            errorType = "SYNTAX_ERROR",
            exactMistake = "unclosed brace in function body"
        )

        val netScore = tracker.getNetRewardScore("groq")
        assertEquals(-1.0, netScore)
        assertEquals(1, tracker.getRecentPenaltyCount("groq"))
    }

    @Test
    fun `recordSuccess assigns positive reward plus 1`() {
        tracker.recordSuccess(
            provider = "groq",
            action = "provider.code_generation",
            details = "clean AST verified"
        )

        val netScore = tracker.getNetRewardScore("groq")
        assertEquals(1.0, netScore)
        assertEquals(0, tracker.getRecentPenaltyCount("groq"))
    }

    @Test
    fun `net dopamine score reflects cumulative penalties and rewards`() {
        tracker.recordFailure("gemini", "code_gen", "SYNTAX_ERROR", "broken syntax")
        tracker.recordFailure("gemini", "code_gen", "HOLLOW_STUB", "TODO() found")
        tracker.recordSuccess("gemini", "code_gen")

        val netScore = tracker.getNetRewardScore("gemini")
        // -1.0 + -1.0 + 1.0 = -1.0
        assertEquals(-1.0, netScore)
    }
}
