/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.bridge

import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URI
import atropos.bridge.http.EngineHttpServer
import atropos.bridge.http.HttpRoute
import atropos.bridge.http.HttpRouteTable
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AtroposBridgeTest {

    private var server: atropos.bridge.http.EngineHttpServer? = null

    @AfterTest
    fun tearDown() {
        server?.stop()
    }

    private fun startOnEphemeralPort(): Int {
        val started = AtroposBridge.server(0, activeProvider = { "test-provider" })
        server = started
        assertTrue(started.start(), "bridge failed to bind: ${started.lastError()}")
        return assertNotNull(started.boundPort())
    }

    private fun get(port: Int, path: String): Pair<Int, String> {
        val connection = URI("http://127.0.0.1:$port$path").toURL().openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 5_000
        connection.readTimeout = 5_000
        val status = connection.responseCode
        val stream = if (status < 400) connection.inputStream else connection.errorStream
        val body = stream?.let { BufferedReader(InputStreamReader(it)).use(BufferedReader::readText) } ?: ""
        connection.disconnect()
        return status to body
    }

    @Test
    fun `the bridge is off unless the operator sets the port variable`() {
        assertNull(AtroposBridge.fromEnvironment(environment = { null }, activeProvider = { "p" }), "a listener must never open by default")
        assertNull(AtroposBridge.fromEnvironment(environment = { "" }, activeProvider = { "p" }))
        assertNull(AtroposBridge.fromEnvironment(environment = { "not-a-port" }, activeProvider = { "p" }))
        assertNull(AtroposBridge.fromEnvironment(environment = { "70000" }, activeProvider = { "p" }))
    }

    @Test
    fun `an explicit port variable constructs a server`() {
        assertNotNull(AtroposBridge.fromEnvironment(environment = { "0" }, activeProvider = { "p" }))
    }

    @Test
    fun `health answers over real HTTP`() {
        val port = startOnEphemeralPort()

        val (status, body) = get(port, "/v1/health")

        assertEquals(200, status)
        assertTrue(body.contains("\"ok\":true"), body)
        assertTrue(body.contains("atropos"), body)
    }

    @Test
    fun `routes describes what this build exposes`() {
        val port = startOnEphemeralPort()

        val (status, body) = get(port, "/v1/routes")

        assertEquals(200, status)
        listOf("/v1/health", "/v1/answers", "/v1/projects", "/v1/commands", "/v1/vocabulary", "/v1/cli", "/v1/approve", "/v1/session")
            .forEach { assertTrue(body.contains(it), "route $it missing from description") }
    }

    @Test
    fun `the command registry crosses the wire from the single owner`() {
        val port = startOnEphemeralPort()

        val (status, body) = get(port, "/v1/commands")

        assertEquals(200, status)
        assertTrue(body.contains("quickAccess"))
        assertTrue(body.contains("sections"))
        assertTrue(body.contains("/help"))
    }

    @Test
    fun `both vocabularies are served and stay separate`() {
        val port = startOnEphemeralPort()

        val (status, body) = get(port, "/v1/vocabulary")

        assertEquals(200, status)
        assertTrue(body.contains("review-required"), "Doc 4 status term missing")
        assertTrue(body.contains("implemented"), "P20-G09 completion term missing")
        assertTrue(body.contains("\"isPositiveClaim\":true"))
        // The collapse P20-G09 forbids would show up as one term list, not two.
        assertTrue(body.contains("\"status\"") && body.contains("\"completion\""))
    }

    @Test
    fun `an unknown path is refused with a reason and a remedy`() {
        val port = startOnEphemeralPort()

        val (status, body) = get(port, "/v1/does-not-exist")

        assertEquals(404, status)
        assertTrue(body.contains("unknown-route"))
        assertTrue(body.contains("remedy"))
    }

    @Test
    fun `the bridge reports its own liveness and port`() {
        val port = startOnEphemeralPort()

        assertTrue(assertNotNull(server).isRunning())
        assertTrue(port > 0)
        assertNull(assertNotNull(server).lastError())
    }

    @Test
    fun `server diagnostic sink redacts route exceptions`() {
        val direct = EngineHttpServer(
            routeTable = HttpRouteTable(
                listOf(HttpRoute("GET", "/boom", "test") {
                    error("provider api_key=sk-live-secret-123456789")
                })
            ),
            port = 0
        )
        server = direct
        assertTrue(direct.start())

        val (status, body) = get(assertNotNull(direct.boundPort()), "/boom")

        assertEquals(500, status)
        assertTrue(body.contains("engine-error"))
        assertTrue(direct.lastError()?.contains("sk-live-secret-123456789") == false)
        assertTrue(direct.lastError()?.contains("<redacted:api_key>") == true)
    }
}
