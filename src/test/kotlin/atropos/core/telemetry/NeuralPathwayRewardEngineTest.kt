/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.telemetry

import atropos.core.dopamine.NeuralPathwayRewardEngine
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NeuralPathwayRewardEngineTest {

    @Test
    fun `test base signal values conform exactly to specification`() {
        val approved = NeuralPathwayRewardEngine.computeScore(NeuralPathwayRewardEngine.SignalCategory.APPROVED_HUNK)
        assertEquals(1.0, approved.finalScore, 1e-6)

        val rejected = NeuralPathwayRewardEngine.computeScore(NeuralPathwayRewardEngine.SignalCategory.REJECTED_HUNK)
        assertEquals(-1.0, rejected.finalScore, 1e-6)

        val vacuous = NeuralPathwayRewardEngine.computeScore(NeuralPathwayRewardEngine.SignalCategory.VACUOUS_ASSERTION)
        assertEquals(-1.5, vacuous.finalScore, 1e-6)

        val survivingMutant = NeuralPathwayRewardEngine.computeScore(NeuralPathwayRewardEngine.SignalCategory.SURVIVING_MUTANT)
        assertEquals(-1.5, survivingMutant.finalScore, 1e-6)

        val fraud = NeuralPathwayRewardEngine.computeScore(NeuralPathwayRewardEngine.SignalCategory.FRAUD_ATTEMPT)
        assertEquals(-10.0, fraud.finalScore, 1e-6)

        val fix = NeuralPathwayRewardEngine.computeScore(NeuralPathwayRewardEngine.SignalCategory.DEFINITIVE_FIX)
        assertEquals(2.0, fix.finalScore, 1e-6)
    }

    @Test
    fun `test repeated exact error escalation multiplier 1_2 per occurrence`() {
        val occurrence0 = NeuralPathwayRewardEngine.computeScore(NeuralPathwayRewardEngine.SignalCategory.REJECTED_HUNK, priorOccurrenceCount = 0)
        assertEquals(-1.0, occurrence0.finalScore, 1e-6)

        val occurrence1 = NeuralPathwayRewardEngine.computeScore(NeuralPathwayRewardEngine.SignalCategory.REJECTED_HUNK, priorOccurrenceCount = 1)
        assertEquals(-1.2, occurrence1.finalScore, 1e-6)

        val occurrence2 = NeuralPathwayRewardEngine.computeScore(NeuralPathwayRewardEngine.SignalCategory.REJECTED_HUNK, priorOccurrenceCount = 2)
        assertEquals(-1.44, occurrence2.finalScore, 1e-6)
    }

    @Test
    fun `test related error value amplification adds 0_15 multiplier`() {
        val standalone = NeuralPathwayRewardEngine.computeScore(NeuralPathwayRewardEngine.SignalCategory.REJECTED_HUNK, sharesClusterWithPriorError = false)
        val related = NeuralPathwayRewardEngine.computeScore(NeuralPathwayRewardEngine.SignalCategory.REJECTED_HUNK, sharesClusterWithPriorError = true)

        assertTrue(abs(related.finalScore) > abs(standalone.finalScore))
        assertEquals(-1.15, related.finalScore, 1e-6)
    }
}
