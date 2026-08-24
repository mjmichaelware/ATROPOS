/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.bridge

import atropos.bridge.http.HttpRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BridgeQuotaRouteTest {
    @Test
    fun quota_route_uses_the_bound_status_projection_supplier() {
        val table = BridgeRoutes(quotaSummary = { "{\"readable\":true,\"providers\":[]}" }).table()
        val response = table.resolve(HttpRequest("GET", "/v1/quota", emptyMap(), emptyMap(), ""))
        assertEquals(200, response.status)
        assertTrue(response.body.contains("\"readable\":true"))
    }
}
