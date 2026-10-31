/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.datacenter.atoms.prm

/**
 * PointwisePairwiseProcessLoss
 *
 * Implements objective loss evaluations for PRM process reward models:
 * 1. Pointwise Binary Cross Entropy with soft targets
 * 2. Pairwise Margin Ranking loss for sibling AST search branches
 */
class PointwisePairwiseProcessLoss(
    private val marginXi: Double = 0.2
) {
    fun computePointwiseBce(predictedScore: Double, targetLabel: Double): Double {
        val p = predictedScore.coerceIn(1e-7, 1.0 - 1e-7)
        val y = targetLabel.coerceIn(0.0, 1.0)
        return -(y * Math.log(p) + (1.0 - y) * Math.log(1.0 - p))
    }

    fun computePairwiseMarginLoss(positiveScore: Double, negativeScore: Double): Double {
        val diff = positiveScore - negativeScore
        return Math.max(0.0, marginXi - diff)
    }

    companion object {
        val defaultLoss = PointwisePairwiseProcessLoss()
    }
}
