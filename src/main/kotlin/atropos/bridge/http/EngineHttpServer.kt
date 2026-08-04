/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.bridge.http

import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * The engine's request-reading listener.
 *
 * Source Doc 4 requires Web and Android to be clients of the same engine, and
 * a client that cannot ask a question is not a client. The existing
 * `RunObserver` streams observations but never parses a request, so it can
 * answer exactly one question; this server exists to answer the rest.
 *
 * It holds no product logic. It accepts a connection, parses, routes, writes,
 * closes — every answer comes from a handler that composes an existing owner.
 * That boundary is what keeps §0's "no second event system" true: this is
 * transport, not a second engine.
 *
 * Binding is loopback-only and not configurable. The handlers below reach
 * durable operator state, so a bind address is a security decision, not a
 * preference — exposing it on a routable interface would publish an operator's
 * engine to their network.
 */
class EngineHttpServer(
    private val routeTable: HttpRouteTable,
    private val port: Int = DEFAULT_PORT,
    private val parser: HttpRequestParser = HttpRequestParser(),
    private val writer: HttpResponseWriter = HttpResponseWriter()
) {
    private val running = AtomicBoolean(false)
    private val socketRef = AtomicReference<ServerSocket?>(null)
    private val lastErrorRef = AtomicReference<String?>(null)
    private val workers = Executors.newFixedThreadPool(MAX_WORKERS) { task ->
        Thread(task, "atropos-bridge").apply { isDaemon = true }
    }

    fun isRunning(): Boolean = running.get()

    fun lastError(): String? = lastErrorRef.get()

    /** The bound port, which differs from [port] when 0 asked the OS to choose. */
    fun boundPort(): Int? = socketRef.get()?.localPort

    fun start(): Boolean {
        if (!running.compareAndSet(false, true)) return false
        return try {
            val socket = ServerSocket()
            socket.reuseAddress = true
            socket.bind(InetSocketAddress(InetAddress.getLoopbackAddress(), port))
            socketRef.set(socket)
            lastErrorRef.set(null)
            Thread({ acceptLoop(socket) }, "atropos-bridge-accept").apply {
                isDaemon = true
                start()
            }
            true
        } catch (e: Exception) {
            running.set(false)
            lastErrorRef.set(e.message ?: "bridge failed to bind")
            false
        }
    }

    fun stop() {
        running.set(false)
        runCatching { socketRef.getAndSet(null)?.close() }
        workers.shutdownNow()
    }

    private fun acceptLoop(socket: ServerSocket) {
        while (running.get() && !socket.isClosed) {
            val client = try {
                socket.accept()
            } catch (e: Exception) {
                if (running.get()) lastErrorRef.set(e.message ?: "accept failed")
                return
            }
            // A rejected connection is reported, never silently dropped: a
            // surface that saw nothing cannot distinguish a busy engine from a
            // dead one.
            runCatching { workers.submit { handle(client) } }.onFailure {
                runCatching { client.close() }
            }
        }
    }

    private fun handle(client: Socket) {
        client.use { socket ->
            socket.soTimeout = READ_TIMEOUT_MILLIS
            val response = try {
                val reader = BufferedReader(InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))
                val request = parser.parse(reader)
                    ?: return@use writer.write(
                        socket.getOutputStream(),
                        HttpResponse.badRequest(
                            "The request could not be parsed within the bridge's bounds.",
                            "Send a well-formed HTTP/1.1 request under the size limits."
                        )
                    )
                routeTable.resolve(request)
            } catch (e: Exception) {
                // The reason is recorded for the operator but never returned:
                // an exception message can carry a path or a value, and this
                // response leaves the process.
                lastErrorRef.set(e.message ?: "request failed")
                HttpResponse.refusal(
                    500,
                    "engine-error",
                    "The engine failed while answering this request.",
                    "Run the same query in the CLI to reproduce it."
                )
            }
            runCatching { writer.write(socket.getOutputStream(), response) }
        }
    }

    private companion object {
        const val DEFAULT_PORT = 4317
        const val MAX_WORKERS = 8
        const val READ_TIMEOUT_MILLIS = 15_000
    }
}
