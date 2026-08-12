/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.bridge.http

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HttpRouteTableTest {

    private fun request(method: String, path: String) =
        HttpRequest(method, path, emptyMap(), emptyMap(), "")

    private val table = HttpRouteTable(
        listOf(
            HttpRoute("GET", "/v1/health", "liveness") { HttpResponse.json("""{"ok":true}""") }
        )
    )

    @Test
    fun `routes an exact method and path`() {
        val response = table.resolve(request("GET", "/v1/health"))

        assertEquals(200, response.status)
        assertEquals("""{"ok":true}""", response.body)
    }

    @Test
    fun `a known path with the wrong verb is 405, not 404`() {
        val response = table.resolve(request("POST", "/v1/health"))

        assertEquals(405, response.status)
        assertTrue(response.body.contains("method-not-allowed"))
    }

    @Test
    fun `an unknown path is 404 and never falls through to a handler`() {
        val response = table.resolve(request("GET", "/v1/health/../secrets"))

        assertEquals(404, response.status)
        assertTrue(response.body.contains("unknown-route"))
    }

    @Test
    fun `a path that merely shares a prefix does not match`() {
        val response = table.resolve(request("GET", "/v1/healthcheck"))

        assertEquals(404, response.status, "prefix matching would hand this to the health handler")
    }

    @Test
    fun `describe lists the routes this build exposes`() {
        val described = table.describe()

        assertTrue(described.contains("/v1/health"))
        assertTrue(described.contains("liveness"))
    }

    @Test
    fun `every refusal states a reason and a remedy`() {
        listOf(
            table.resolve(request("GET", "/nope")),
            table.resolve(request("DELETE", "/v1/health"))
        ).forEach { response ->
            assertTrue(response.body.contains("\"reason\""), response.body)
            assertTrue(response.body.contains("\"remedy\""), response.body)
        }
    }
}
