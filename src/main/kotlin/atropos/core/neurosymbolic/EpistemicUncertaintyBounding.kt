/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.neurosymbolic

import atropos.core.confidence.ParetoConfidence
import atropos.core.research.PromptArtifact

class EpistemicUncertaintyBounding {

    fun checkBound(confidence: ParetoConfidence, epsilon: Double = 0.35): Boolean {
        val uncertainty = 1.0 - confidence.clarity
        return uncertainty <= epsilon
    }

    fun evaluateOrConstructDecisionPause(
        confidence: ParetoConfidence,
        prompt: PromptArtifact,
        epsilon: Double = 0.35
    ): String? {
        val uncertainty = 1.0 - confidence.clarity
        if (uncertainty > epsilon) {
            return "PARETO_DECISION_PAUSE: Epistemic uncertainty $uncertainty > $epsilon for goal '${prompt.projectId}'. Trade-off resolution required before synthesis."
        }
        return null
    }
}
