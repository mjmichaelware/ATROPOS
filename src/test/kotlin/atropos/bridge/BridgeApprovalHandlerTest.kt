/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.bridge

import atropos.bridge.http.HttpRequest
import atropos.core.approval.PendingApprovalStore
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BridgeApprovalHandlerTest {
    @Test
    fun approval_requires_attribution_and_cannot_be_decided_twice() {
        val store = PendingApprovalStore(Files.createTempDirectory("bridge-approval-"))
        val pending = store.record("proposal-1", "worker-1", "paid-provider", listOf("."), "spend")
        val handler = BridgeApprovalHandler(store)

        val missingActor = handler.decideApproval(
            HttpRequest("POST", "/v1/approvals/decide", mapOf("id" to pending.id, "approved" to "true"), emptyMap(), "")
        )
        assertEquals(403, missingActor.status)
        assertTrue(missingActor.body.contains("attribution-required"))

        val recorded = handler.decideApproval(
            HttpRequest(
                "POST",
                "/v1/approvals/decide",
                emptyMap(),
                emptyMap(),
                "id=${pending.id}&approved=true&decidedBy=operator-1"
            )
        )
        assertEquals(200, recorded.status)
        assertTrue(recorded.body.contains("\"approved\":true"))

        val second = handler.decideApproval(
            HttpRequest(
                "POST",
                "/v1/approvals/decide",
                mapOf("id" to pending.id, "approved" to "false", "decidedBy" to "operator-2"),
                emptyMap(),
                ""
            )
        )
        assertEquals(409, second.status)
        assertTrue(second.body.contains("approval-refused"))
    }
}
