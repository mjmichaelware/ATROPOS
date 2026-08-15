/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.verification

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IntegrityProofsTest {

    @Test
    fun `selfhost proof passes when status contains modified`() {
        val res = SelfHostProof.runProof("State.kt", "modified: State.kt")
        assertEquals("VERIFIED", res.verdict)
    }

    @Test
    fun `greenfield proof passes when absent count greater than zero`() {
        val res = GreenfieldFactoryProof.runProof(5)
        assertEquals("VERIFIED", res.verdict)
    }

    @Test
    fun `longhorizon proof passes for many steps`() {
        val res = LongHorizonProof.runProof(12)
        assertEquals("VERIFIED", res.verdict)
    }

    @Test
    fun `recovery proof passes when recoveredState is true`() {
        val res = RecoveryProof.runProof(true)
        assertEquals("VERIFIED", res.verdict)
    }

    @Test
    fun `safety proof passes when zero leaks and zero bounds violations`() {
        val res = SafetyProof.runProof(0, 0)
        assertEquals("VERIFIED", res.verdict)
    }

    @Test
    fun `fallback proof passes when fallbackChainsTriggered is true`() {
        val res = FallbackProof.runProof(true)
        assertEquals("VERIFIED", res.verdict)
    }

    @Test
    fun `learning proof passes when accuracyImprovement is positive`() {
        val res = LearningProof.runProof(0.12)
        assertEquals("VERIFIED", res.verdict)
    }

    @Test
    fun `hashintegrity proof passes when JAR hashes are identical`() {
        val res = HashIntegrityProof.runProof("hash123", "hash123")
        assertEquals("VERIFIED", res.verdict)
    }
}
