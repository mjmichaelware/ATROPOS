/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.actuation

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GatedComputerUseBridgeTest {

    @Test
    fun `evaluation refuses actuation outside territory`() {
        val bridge = GatedComputerUseBridge()
        val proposal = ActuationProposal(
            actionId = "act-1",
            actionType = "mouse_click",
            target = ActuationTarget(
                surfaceName = "external_window",
                coordinates = Pair(100, 200),
                withinTerritory = false
            ),
            payloadSummary = "click outside declared bounds"
        )
        val result = bridge.evaluateProposal(proposal, "agent-1", "grant-1")
        assertFalse(result.allowed)
        assertTrue(result.reason.contains("outside authorized territory"))
    }
}
