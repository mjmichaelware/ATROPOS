/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.confidence

import atropos.core.research.ResearchDocument
import java.security.MessageDigest
import kotlin.math.max

enum class ConfidenceAction {
    DISPATCH_WAVE_0,
    INVOKE_SUGGESTION_PASS,
    PARETO_DECISION_PAUSE
}

class HysteresisConfidenceEngine(
    val thetaPause: Double = 0.650,
    val thetaSuggest: Double = 0.850
) {
    init {
        require(thetaPause > 0.0) { "theta_pause must be strictly positive" }
        require(thetaPause <= thetaSuggest) { "theta_pause must be <= theta_suggest" }
        require(thetaSuggest < 1.0) { "theta_suggest must be strictly less than 1.0" }
    }

    private val w1: Double = 0.25
    private val w4: Double = 0.25

    fun evaluate(doc: ResearchDocument): ConfidenceAction {
        val score = doc.confidence.score
        return when {
            score < thetaPause -> ConfidenceAction.PARETO_DECISION_PAUSE
            score < thetaSuggest -> ConfidenceAction.INVOKE_SUGGESTION_PASS
            else -> ConfidenceAction.DISPATCH_WAVE_0
        }
    }

    /**
     * Closed-form update (Bug-4 fix):
     * C_{t+1} = C_t + w4 * (m_resolved / |Spans|) + w1 * (1 - C_t) * (|A_Q| / (|A_Q| + 1))
     *
     * Result strictly bounded in [0.0, 0.9999].
     */
    fun updateConfidence(
        cCurrent: Double,
        spansTotal: Int,
        resolvedGaps: Int,
        answersCount: Int
    ): Double {
        val spans = max(1, spansTotal).toDouble()
        val mResolved = max(0, resolvedGaps).toDouble()
        val aq = max(0, answersCount).toDouble()

        val gapTerm = w4 * (mResolved / spans)
        val answerTerm = w1 * (1.0 - cCurrent.coerceIn(0.0, 1.0)) * (aq / (aq + 1.0))

        val cNext = cCurrent + gapTerm + answerTerm
        return cNext.coerceIn(0.0, 0.9999)
    }
}
