/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.bridge.http

import atropos.core.security.RedactionFilter
import atropos.bridge.terminal.TerminalSession
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Base64
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
    private val writer: HttpResponseWriter = HttpResponseWriter(),
    private val authenticator: HttpRequestAuthenticator = HttpRequestAuthenticator(null),
    /**
     * Long-lived routes, matched before the request routes.
     *
     * Separate because a stream owns its socket for as long as the client
     * stays: giving it to the request path would leave the response writer
     * trying to close a connection somebody else is still writing to.
     */
    private val streamRoutes: List<HttpStreamRoute> = emptyList()
) {
    private val redactionFilter = RedactionFilter()
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
            lastErrorRef.set(redactionFilter.compact(e.message ?: "bridge failed to bind"))
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
                if (running.get()) lastErrorRef.set(redactionFilter.compact(e.message ?: "accept failed"))
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

                authenticator.authorize(request)?.let { refusal ->
                    return@use writer.write(socket.getOutputStream(), refusal)
                }

                // Check for WebSocket upgrade request
                if (isWebSocketUpgrade(request)) {
                    return@use handleWebSocketUpgrade(socket, request)
                }

                val stream = streamRoutes.firstOrNull {
                    it.method.equals(request.method, ignoreCase = true) && it.path == request.path
                }
                if (stream != null) return@use serveStream(socket, request, stream)

                routeTable.resolve(request)
            } catch (e: Exception) {
                // The reason is recorded for the operator but never returned:
                // an exception message can carry a path or a value, and this
                // response leaves the process.
                lastErrorRef.set(redactionFilter.compact(e.message ?: "request failed"))
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

    /**
     * Checks if the request is a WebSocket upgrade request.
     */
    private fun isWebSocketUpgrade(request: HttpRequest): Boolean {
        return request.headers["upgrade"]?.equals("websocket", ignoreCase = true) == true &&
               request.headers["connection"]?.contains("upgrade", ignoreCase = true) == true
    }

    /**
     * Handles WebSocket upgrade handshake (RFC 6455).
     */
    private fun handleWebSocketUpgrade(socket: Socket, request: HttpRequest) {
        val webSocketKey = request.headers["sec-websocket-key"]
            ?: return writer.write(socket.getOutputStream(), HttpResponse.refusal(
                400, "bad-request", "Missing Sec-WebSocket-Key header", "Include Sec-WebSocket-Key header"
            ))

        val acceptKey = computeWebSocketAccept(webSocketKey)
        val response = HttpResponse(
            101,
            "Switching Protocols",
            mapOf(
                "Upgrade" to "websocket",
                "Connection" to "Upgrade",
                "Sec-WebSocket-Accept" to acceptKey
            ),
            ""
        )
        writer.write(socket.getOutputStream(), response)

        // Upgrade successful - handle WebSocket frames
        handleWebSocket(socket, request)
    }

    /**
     * Computes the Sec-WebSocket-Accept value per RFC 6455.
     */
    private fun computeWebSocketAccept(key: String): String {
        val guid = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11"
        val sha1 = MessageDigest.getInstance("SHA-1")
        val hash = sha1.digest((key + guid).toByteArray(StandardCharsets.UTF_8))
        return Base64.getEncoder().encodeToString(hash)
    }

    /**
     * Handles WebSocket frame communication (RFC 6455).
     */
    private fun handleWebSocket(socket: Socket, request: HttpRequest) {
        val path = request.path
        if (path != "/v1/terminal") {
            socket.close()
            return
        }

        socket.soTimeout = 0
        val out = socket.getOutputStream()
        val in = socket.getInputStream()

        // Extract projectId from query
        val projectId = request.query.get("projectId")

        // Spawn PTY terminal session
        val terminal = TerminalSession.spawn(projectId)
        var running = true

        // Thread to read from PTY and send to WebSocket
        val ptyReaderThread = Thread({
            val buffer = ByteArray(4096)
            while (running.get() && !socket.isClosed) {
                try {
                    val n = terminal.read(buffer)
                    if (n > 0) {
                        val frame = encodeWebSocketFrame(buffer, 0, n)
                        out.write(frame)
                        out.flush()
                    } else {
                        break
                    }
                } catch (e: Exception) {
                    break
                }
            }
        }, "atropos-terminal-pty-reader").apply { isDaemon = true; start() }

        // Main loop: read WebSocket frames from client and write to PTY
        val buffer = ByteBuffer.allocate(4096)
        while (running.get() && !socket.isClosed) {
            try {
                // Read WebSocket frame
                val opcodeAndFin = in.read()
                if (opcodeAndFin == -1) break
                val fin = (opcodeAndFin and 0x80) != 0
                val opcode = opcodeAndFin and 0x0F

                val maskAndLen = in.read()
                if (maskAndLen == -1) break
                val masked = (maskAndLen and 0x80) != 0
                var payloadLen = maskAndLen and 0x7F

                if (payloadLen == 126) {
                    payloadLen = (in.read() shl 8) or in.read()
                } else if (payloadLen == 127) {
                    // 64-bit length (not fully supported, cap at Int.MAX_VALUE)
                    var len = 0L
                    repeat(8) { len = (len shl 8) or (in.read().toLong() and 0xFF) }
                    payloadLen = len.coerceAtMost(Int.MAX_VALUE).toInt()
                }

                var mask = ByteArray(4)
                if (masked) {
                    in.read(mask)
                }

                val payload = ByteArray(payloadLen)
                var read = 0
                while (read < payloadLen) {
                    val n = in.read(payload, read, payloadLen - read)
                    if (n <= 0) break
                    read += n
                }

                if (masked) {
                    for (i in 0 until payloadLen) {
                        payload[i] = (payload[i] xor mask[i % 4]).toByte()
                    }
                }

                when (opcode) {
                    0x8 -> { // Close frame
                        // Send close frame back
                        val closeFrame = encodeWebSocketFrame(ByteArray(0), 0x8)
                        out.write(closeFrame)
                        out.flush()
                        break
                    }
                    0x9 -> { // Ping frame
                        // Respond with Pong
                        val pongFrame = encodeWebSocketFrame(ByteArray(0), 0xA)
                        out.write(pongFrame)
                        out.flush()
                    }
                    0xA -> { // Pong frame - ignore
                    }
                    0x1, 0x2 -> { // Text or Binary frame
                        // Write to PTY stdin
                        terminal.write(payload)
                    }
                }
            } catch (e: Exception) {
                break
            }
        }
        terminal.close()
    }

    /**
     * Encodes a WebSocket frame (RFC 6455).
     * Server-to-client frames are NOT masked.
     */
    private fun encodeWebSocketFrame(payload: ByteArray, opcode: Int = 0x1): ByteArray {
        val fin = 0x80
        val masked = 0 // Server frames are not masked
        val len = payload.size

        val header = ByteArrayOutputStream()
        header.write((fin or opcode).toByte())

        if (len < 126) {
            header.write((len or 0).toByte())
        } else if (len <= 0xFFFF) {
            header.write(126.toByte())
            header.write((len shr 8).toByte())
            header.write((len and 0xFF).toByte())
        } else {
            header.write(127.toByte())
            // 64-bit length (simplified for our use case)
            for (i in 7 downTo 0) {
                header.write((payload.size.toLong() shr (i * 8) and 0xFF).toByte())
            }
        }
        header.write(payload)
        return header.toByteArray()
    }

    /**
     * Runs a stream until the client leaves or the server stops.
     *
     * The socket read timeout is cleared for the duration: a stream is expected
     * to be idle between frames, and the request-path timeout would otherwise
     * kill a healthy connection that simply had nothing to say yet. Departure
     * is detected by the write failing, which is the only reliable signal a
     * server gets when a browser closes an EventSource.
     */
    private fun serveStream(socket: Socket, request: HttpRequest, route: HttpStreamRoute) {
        socket.soTimeout = 0
        val out = socket.getOutputStream()
        writer.writeEventStreamHeader(out)
        val sink = object : StreamSink {
            private var open = true
            override fun isOpen(): Boolean = open && running.get() && !socket.isClosed
            override fun emit(event: String, data: String): Boolean {
                if (!isOpen()) return false
                return try {
                    writer.writeEvent(out, event, data)
                    true
                } catch (_: Exception) {
                    open = false
                    false
                }
            }
        }
        try {
            route.handler(request, sink)
        } catch (e: Exception) {
            lastErrorRef.set(redactionFilter.compact(e.message ?: "stream failed"))
        }
    }

    private companion object {
        const val DEFAULT_PORT = 4317
        const val MAX_WORKERS = 8
        const val READ_TIMEOUT_MILLIS = 15_000
    }
}
