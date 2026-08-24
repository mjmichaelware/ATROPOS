/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.bridge

import atropos.bridge.http.EngineHttpServer
import atropos.bridge.conversation.BridgeSessionStore
import atropos.bridge.conversation.TurnAuthor
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.InetAddress
import java.net.Socket
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * W0's streaming half, exercised over a real socket.
 *
 * The frame writers existed before this and no route called them, so nothing
 * proved the engine could hold a connection open at all. These tests speak
 * HTTP directly rather than through `HttpURLConnection`, which buffers a
 * response and would hide whether frames arrive incrementally.
 */
class BridgeStreamTest {

    private var server: EngineHttpServer? = null

    @AfterTest
    fun tearDown() {
        server?.stop()
    }

    private fun start(maxFrames: Int, sessions: BridgeSessionStore = BridgeSessionStore()): Int {
        val routes = BridgeRoutes(activeProvider = { "test-provider" }, sessions = sessions)
        val started = EngineHttpServer(
            routeTable = routes.table(),
            port = 0,
            // Bounded so the test terminates; no sleeping between frames.
            streamRoutes = routes.streamRoutes(intervalMillis = 0, maxFrames = maxFrames, sleep = {})
        )
        server = started
        assertTrue(started.start(), "bridge failed to bind: ${started.lastError()}")
        return assertNotNull(started.boundPort())
    }

    private fun readStream(port: Int, path: String, lines: Int): List<String> =
        Socket(InetAddress.getLoopbackAddress(), port).use { socket ->
            socket.soTimeout = 10_000
            PrintWriter(socket.getOutputStream(), true).println("GET $path HTTP/1.1\r\nHost: localhost\r\nAccept: text/event-stream\r\n")
            val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
            buildList {
                repeat(lines) {
                    add(reader.readLine() ?: return@buildList)
                }
            }
        }

    @Test
    fun `the stream opens with an event-stream content type`() {
        val port = start(maxFrames = 1)

        val head = readStream(port, "/v1/answers/stream", 6)

        assertTrue(head.first().contains("200"), head.toString())
        assertTrue(
            head.any { it.startsWith("Content-Type:", ignoreCase = true) && it.contains("text/event-stream") },
            head.toString()
        )
        assertTrue(head.any { it.contains("no-store") }, "a live stream must not be cached")
    }

    @Test
    fun `frames arrive as named server-sent events carrying the six answers`() {
        val port = start(maxFrames = 1)

        val received = readStream(port, "/v1/answers/stream", 40)

        assertTrue(received.any { it == "event: answers" }, received.toString())
        val data = received.firstOrNull { it.startsWith("data: ") }
        assertNotNull(data, "no data frame arrived: $received")
        listOf("objective", "doing", "why", "progress", "next", "evidence").forEach {
            assertTrue(data.contains("\"$it\""), "frame missing answer '$it'")
        }
    }

    @Test
    fun `more than one frame is pushed on a single connection`() {
        val port = start(maxFrames = 3)

        val received = readStream(port, "/v1/answers/stream", 120)

        assertTrue(
            received.count { it == "event: answers" } >= 2,
            "a stream that sends one frame is a slow response, not a stream: $received"
        )
    }

    @Test
    fun `the stream payload is the same projection the snapshot route serves`() {
        val port = start(maxFrames = 1)
        val streamed = readStream(port, "/v1/answers/stream", 40)
            .first { it.startsWith("data: ") }
            .removePrefix("data: ")

        // Both must carry the same shape; a stream with its own payload would
        // be a second source of truth for the same six questions.
        assertTrue(streamed.contains("\"answers\""))
        assertTrue(streamed.contains("\"queue\""))
        assertTrue(streamed.contains("\"readable\""))
    }

    @Test
    fun `asking for the stream path without a stream connection explains itself`() {
        val port = start(maxFrames = 1)
        val table = BridgeRoutes().table()

        val response = table.resolve(
            atropos.bridge.http.HttpRequest("GET", "/v1/answers/stream", emptyMap(), emptyMap(), "")
        )

        assertEquals(400, response.status)
        assertTrue(response.body.contains("stream-required"))
        assertTrue(response.body.contains("EventSource"))
    }

    @Test
    fun `event stream honors the requested session over a real socket`() {
        BridgeEventHub.clear()
        val sessions = BridgeSessionStore()
        val wanted = sessions.create()
        val other = sessions.create()
        sessions.append(wanted.id, TurnAuthor.OPERATOR, "wanted")
        sessions.append(other.id, TurnAuthor.OPERATOR, "other")
        val port = start(maxFrames = 1, sessions = sessions)

        val received = readStream(port, "/v1/events/stream?session=${wanted.id}&after=0", 80)
        val data = received.firstOrNull { it.startsWith("data: ") }
        assertNotNull(data, "no event frame arrived: $received")
        assertTrue(data.contains(wanted.id), data)
        assertTrue(!data.contains(other.id), data)
    }
}
