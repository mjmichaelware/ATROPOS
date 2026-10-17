/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.bridge.web

import java.io.InputStream
import java.io.OutputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.net.ServerSocket
import java.net.Socket
import java.security.MessageDigest
import java.util.Base64
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean

/**
 * ATOM-WEB-015: OpenCode-Style Binary WebSocket Terminal Mirror.
 *
 * Serves a full-duplex WebSocket stream mirroring CLI xterm.js sessions
 * into browser canvases with 0 REST polling on port 8787.
 * Mathematical Invariant: State(BrowserTerminal) = State(CliTerminal) at all sync points.
 */
class TerminalMirrorSocketServer(val port: Int = 8787) {

    private val running = AtomicBoolean(false)
    private var serverSocket: ServerSocket? = null
    private val clientSockets = CopyOnWriteArrayList<Socket>()
    private val pipedOutput = PipedOutputStream()
    private val pipedInput = PipedInputStream(pipedOutput)

    fun start() {
        if (!running.compareAndSet(false, true)) return

        val server = ServerSocket(port)
        serverSocket = server

        val acceptorThread = Thread({
            while (running.get() && !server.isClosed) {
                try {
                    val client = server.accept()
                    clientSockets.add(client)
                    handleClient(client)
                } catch (_: Exception) {
                    // Socket closed or connection interrupted
                }
            }
        }, "atropos-terminal-mirror-acceptor")
        acceptorThread.isDaemon = true
        acceptorThread.start()
    }

    fun broadcastFrame(frameData: ByteArray) {
        if (frameData.isEmpty() || clientSockets.isEmpty()) return
        val frame = encodeWebSocketBinaryFrame(frameData)
        for (client in clientSockets) {
            try {
                if (!client.isClosed) {
                    val out = client.getOutputStream()
                    out.write(frame)
                    out.flush()
                }
            } catch (_: Exception) {
                try { client.close() } catch (_: Exception) {}
                clientSockets.remove(client)
            }
        }
    }

    fun receiveInput(): InputStream = pipedInput

    fun stop() {
        running.set(false)
        try { serverSocket?.close() } catch (_: Exception) {}
        for (client in clientSockets) {
            try { client.close() } catch (_: Exception) {}
        }
        clientSockets.clear()
        try { pipedOutput.close() } catch (_: Exception) {}
    }

    private fun handleClient(socket: Socket) {
        val clientThread = Thread({
            try {
                val input = socket.getInputStream()
                val output = socket.getOutputStream()

                if (performHandshake(input, output)) {
                    val buffer = ByteArray(4096)
                    while (running.get() && !socket.isClosed) {
                        val firstByte = input.read()
                        if (firstByte == -1) break

                        val opcode = firstByte and 0x0F
                        if (opcode == 0x08) { // Close frame
                            break
                        }

                        val secondByte = input.read()
                        if (secondByte == -1) break
                        val isMasked = (secondByte and 0x80) != 0
                        var payloadLen = (secondByte and 0x7F).toLong()

                        if (payloadLen == 126L) {
                            val b1 = input.read()
                            val b2 = input.read()
                            payloadLen = ((b1 shl 8) or b2).toLong()
                        } else if (payloadLen == 127L) {
                            var len = 0L
                            for (i in 0 until 8) {
                                len = (len shl 8) or (input.read().toLong() and 0xFF)
                            }
                            payloadLen = len
                        }

                        val mask = ByteArray(4)
                        if (isMasked) {
                            input.readNBytes(mask, 0, 4)
                        }

                        val payload = ByteArray(payloadLen.toInt().coerceAtMost(65536))
                        var readSoFar = 0
                        while (readSoFar < payload.size) {
                            val count = input.read(payload, readSoFar, payload.size - readSoFar)
                            if (count == -1) break
                            readSoFar += count
                        }

                        if (isMasked) {
                            for (i in 0 until readSoFar) {
                                payload[i] = (payload[i].toInt() xor mask[i % 4].toInt()).toByte()
                            }
                        }

                        // Pipe client keystrokes directly into engine input stream
                        if (readSoFar > 0) {
                            pipedOutput.write(payload, 0, readSoFar)
                            pipedOutput.flush()
                        }
                    }
                }
            } catch (_: Exception) {
                // Client disconnected
            } finally {
                try { socket.close() } catch (_: Exception) {}
                clientSockets.remove(socket)
            }
        }, "atropos-mirror-client-${socket.port}")
        clientThread.isDaemon = true
        clientThread.start()
    }

    private fun performHandshake(input: InputStream, output: OutputStream): Boolean {
        val reader = input.bufferedReader()
        var line: String? = reader.readLine() ?: return false
        var secKey: String? = null

        while (!line.isNullOrBlank()) {
            val lower = line.lowercase()
            if (lower.startsWith("sec-websocket-key:")) {
                secKey = line.substringAfter(":").trim()
            }
            line = reader.readLine()
        }

        if (secKey == null) return false

        val magic = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11"
        val digest = MessageDigest.getInstance("SHA-1")
        val hash = digest.digest((secKey + magic).toByteArray(Charsets.UTF_8))
        val accept = Base64.getEncoder().encodeToString(hash)

        val response = "HTTP/1.1 101 Switching Protocols\r\n" +
            "Upgrade: websocket\r\n" +
            "Connection: Upgrade\r\n" +
            "Sec-WebSocket-Accept: $accept\r\n\r\n"

        output.write(response.toByteArray(Charsets.UTF_8))
        output.flush()
        return true
    }

    private fun encodeWebSocketBinaryFrame(data: ByteArray): ByteArray {
        val len = data.size
        return when {
            len <= 125 -> {
                val header = byteArrayOf(0x82.toByte(), len.toByte())
                header + data
            }
            len <= 65535 -> {
                val header = byteArrayOf(
                    0x82.toByte(),
                    126.toByte(),
                    ((len shr 8) and 0xFF).toByte(),
                    (len and 0xFF).toByte()
                )
                header + data
            }
            else -> {
                val header = ByteArray(10)
                header[0] = 0x82.toByte()
                header[1] = 127.toByte()
                var temp = len.toLong()
                for (i in 9 downTo 2) {
                    header[i] = (temp and 0xFF).toByte()
                    temp = temp shr 8
                }
                header + data
            }
        }
    }
}
