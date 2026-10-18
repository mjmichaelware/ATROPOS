/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.phase20

import atropos.core.autonomy.RewardPenaltyStore
import atropos.core.dopamine.ProviderDopamineRewardTracker
import atropos.core.provider.ProviderFailureMemoryStore
import java.io.File
import java.nio.file.Files
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class Phase20StructuralMetaAdapterTest {

    private lateinit var metaAdapter: Phase20StructuralMetaAdapter
    private lateinit var rewardTracker: ProviderDopamineRewardTracker
    private lateinit var memoryStore: ProviderFailureMemoryStore
    private lateinit var tempDir: File

    @BeforeTest
    fun setup() {
        tempDir = Files.createTempDirectory("phase20-meta-test").toFile()
        val rewardStore = RewardPenaltyStore(
            storageDir = tempDir,
            storageFile = File(tempDir, "rewards.tsv")
        )
        memoryStore = ProviderFailureMemoryStore()
        rewardTracker = ProviderDopamineRewardTracker(rewardStore, memoryStore)
        metaAdapter = Phase20StructuralMetaAdapter(rewardTracker, memoryStore)
    }

    @Test
    fun `clean provider receives un-altered prompt and standard parameters`() {
        val originalPrompt = "Write a function to calculate Fibonacci"
        val adapted = metaAdapter.adaptPrompt("clean-provider", originalPrompt)
        assertEquals(originalPrompt, adapted)

        val params = metaAdapter.adaptHyperparameters("clean-provider")
        assertEquals(0.70, params.temperature)
        assertFalse(params.strictZeroProse)
    }

    @Test
    fun `offending provider receives adapted prompt with strict zero prose and low temperature`() {
        // Record penalty
        rewardTracker.recordFailure(
            provider = "offending-provider",
            errorType = "SYNTAX_VIOLATION",
            exactMistake = "missing closing parenthesis"
        )

        val originalPrompt = "Write a parser function"
        val adapted = metaAdapter.adaptPrompt("offending-provider", originalPrompt)

        assertTrue(adapted.contains("PHASE 20 META-ADAPTATION"))
        assertTrue(adapted.contains("ZERO PROSE"))
        assertTrue(adapted.contains("missing closing parenthesis"))

        val params = metaAdapter.adaptHyperparameters("offending-provider")
        assertEquals(0.15, params.temperature)
        assertTrue(params.strictZeroProse)
    }

    @Test
    fun `self-build candidate validation enforces permanent long-term memory constraints`() {
        // Trigger 2-strike escalation
        memoryStore.recordMistake("test-provider", "STUB_ERROR", "forbidden TODO() detected")
        memoryStore.recordMistake("test-provider", "STUB_ERROR", "forbidden TODO() detected")

        val invalidCandidate = """
            class Candidate {
                fun run() {
                    TODO()
                }
            }
        """.trimIndent()

        val violations = metaAdapter.validateCandidateAgainstPermanentConstraints("test-provider", invalidCandidate)
        assertTrue(violations.isNotEmpty())
        assertTrue(violations.any { it.contains("TODO()") })

        val validCandidate = """
            class Candidate {
                fun run(): Int = 42
            }
        """.trimIndent()

        val validViolations = metaAdapter.validateCandidateAgainstPermanentConstraints("test-provider", validCandidate)
        assertTrue(validViolations.isEmpty())
    }
}
