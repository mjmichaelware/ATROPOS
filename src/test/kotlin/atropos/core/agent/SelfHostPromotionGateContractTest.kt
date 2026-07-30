package atropos.core.agent

import atropos.core.verification.CompletionGateReport
import atropos.core.verification.GateResult
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class SelfHostPromotionGateContractTest {
    private val now = Instant.parse("2026-07-29T00:00:00Z")

    @Test
    fun empty_green_report_is_not_swap_authorization() {
        val refusal = SelfHostPromotionGateContract().refusal(
            CompletionGateReport("node-1", canComplete = true, gateResults = emptyList(), message = "green"),
            expectedNodeId = "node-1"
        )

        assertNotNull(refusal)
    }

    @Test
    fun all_named_passing_results_for_the_selected_node_are_authorized() {
        val refusal = SelfHostPromotionGateContract().refusal(
            CompletionGateReport(
                nodeId = "node-1",
                canComplete = true,
                gateResults = listOf(GateResult("node-1", true, "Compile Gate", "passed", now)),
                message = "all gates passed"
            ),
            expectedNodeId = "node-1"
        )

        assertNull(refusal)
    }

    @Test
    fun passing_evidence_for_another_node_is_not_swap_authorization() {
        val refusal = SelfHostPromotionGateContract().refusal(
            CompletionGateReport(
                nodeId = "node-1",
                canComplete = true,
                gateResults = listOf(GateResult("node-2", true, "Compile Gate", "passed", now)),
                message = "all gates passed"
            ),
            expectedNodeId = "node-1"
        )

        assertNotNull(refusal)
    }

    @Test
    fun unsafe_gate_evidence_is_not_swap_authorization() {
        val refusal = SelfHostPromotionGateContract().refusal(
            CompletionGateReport(
                nodeId = "node-1",
                canComplete = true,
                gateResults = listOf(
                    GateResult("node-1", true, "Self approval", "self verification passed", now)
                ),
                message = "all gates passed"
            ),
            expectedNodeId = "node-1"
        )

        assertNotNull(refusal)
    }
}
