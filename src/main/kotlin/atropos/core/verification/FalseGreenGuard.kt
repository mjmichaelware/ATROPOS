/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.verification

import atropos.core.dag.DagNode
import java.time.Instant

data class FalseGreenAssessment(val passed: Boolean, val reasons: List<String>)

/** Fail-closed checks for completion claims that look green without proof. */
class FalseGreenGuard {
    fun assess(node: DagNode): FalseGreenAssessment {
        val reasons = buildList {
            if (node.state == atropos.core.dag.DagNodeState.COMPLETE && node.expectedOutputs.isEmpty()) {
                add("completed node has no expected outputs")
            }
            if (node.actionPayload.isNullOrBlank() && node.state == atropos.core.dag.DagNodeState.COMPLETE) {
                add("completed node has no action payload")
            }
            if (node.territory.isEmpty() && node.state == atropos.core.dag.DagNodeState.COMPLETE) {
                add("completed node has no declared territory")
            }
        }
        return FalseGreenAssessment(reasons.isEmpty(), reasons)
    }
}

/** Tracks repeated identical outcomes so a loop cannot report retry as progress. */
class AntiOscillation(private val maxRepeats: Int = 2) {
    private val outcomes = linkedMapOf<String, Int>()

    init { require(maxRepeats > 0) }

    fun observe(signature: String, at: Instant = Instant.now()): Boolean {
        require(signature.isNotBlank())
        val count = (outcomes[signature] ?: 0) + 1
        outcomes[signature] = count
        return count <= maxRepeats
    }

    fun count(signature: String): Int = outcomes[signature] ?: 0
}
