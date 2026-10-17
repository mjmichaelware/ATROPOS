/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.confidence

import kotlin.math.ln

/**
 * SD7-T-041: Shannon epistemic entropy estimator over domain probability distributions.
 * Mathematical Invariant: False-Green Tolerance = 0.0000%
 * H(Intent|Prompt,DLOI) = -Σ P(Domain_k|Prompt) log₂ P(Domain_k|Prompt)
 * If H > EPSILON, emit ParetoDecisionEnvelope and await single-bit selection.
 */
object ShannonEntropy {
    const val EPSILON: Double = 0.45
    private val LN_2 = ln(2.0)

    fun compute(domainProbabilities: List<Double>): Double {
        if (domainProbabilities.isEmpty()) return 0.0
        val sum = domainProbabilities.sum()
        if (sum <= 0.0) return 0.0

        var entropy = 0.0
        for (prob in domainProbabilities) {
            val p = prob / sum
            if (p > 1e-12) {
                entropy -= p * (ln(p) / LN_2)
            }
        }
        return entropy
    }

    fun isClarificationRequired(entropy: Double): Boolean = entropy > EPSILON
}
