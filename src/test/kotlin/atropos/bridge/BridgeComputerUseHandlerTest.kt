/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.bridge

import atropos.bridge.http.HttpRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BridgeComputerUseHandlerTest {
    @Test
    fun `computer-use judge refuses an unbounded request`() {
        val response = BridgeComputerUseHandler().judge(
            HttpRequest("POST", "/v1/computer-use/judge", emptyMap(), emptyMap(), "")
        )
        assertEquals(400, response.status)
        assertTrue(response.body.contains("callerId"))
    }

    @Test
    fun `computer-use route is exposed without exposing a shell route`() {
        val table = BridgeRoutes().table()
        val route = table.resolve(
            HttpRequest(
                "POST", "/v1/computer-use/judge",
                mapOf("callerId" to "phone", "operation" to "inspect", "paths" to "src/main", "targetSurface" to "screen", "territoryGrantId" to "grant-1"),
                emptyMap(), ""
            )
        )
        assertTrue(route.status != 404)
        assertTrue(!BridgeRoutes().table().describe().contains("/shell"))
    }
}
