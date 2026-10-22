/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.actuation

import atropos.core.phase20.ComputerUseBridge
import atropos.core.integration.InboundGateResult

data class ActuationTarget(
    val surfaceName: String,
    val coordinates: Pair<Int, Int>? = null,
    val withinTerritory: Boolean = true
)

data class ActuationProposal(
    val actionId: String,
    val actionType: String,
    val target: ActuationTarget,
    val payloadSummary: String
)

/**
 * ADD-W-031 Backend Bridge: Evaluates computer-use actuation requests against territory boundaries
 * and delegated Phase 20 authority gates.
 */
class GatedComputerUseBridge(
    private val delegate: ComputerUseBridge = ComputerUseBridge()
) {
    fun evaluateProposal(
        proposal: ActuationProposal,
        callerId: String,
        territoryGrantId: String
    ): InboundGateResult {
        if (!proposal.target.withinTerritory) {
            return InboundGateResult(
                allowed = false,
                reason = "Actuation refused: Target is outside authorized territory"
            )
        }
        return delegate.judge(
            callerId = callerId,
            operation = proposal.actionType,
            paths = emptyList(),
            targetSurface = proposal.target.surfaceName,
            territoryGrantId = territoryGrantId
        )
    }
}
