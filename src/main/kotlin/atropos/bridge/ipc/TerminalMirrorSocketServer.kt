/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.bridge.ipc

import java.io.InputStream
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean

enum class IpcFrameType(val opcode: Byte) {
    OUTPUT(0x01),
    INPUT(0x02),
    RESIZE(0x03),
    PING(0x00);

    companion object {
        fun fromOpcode(b: Byte): IpcFrameType? = entries.firstOrNull { it.opcode == b }
    }
}

data class TerminalWindowSize(val columns: Int, val rows: Int)

class TerminalMirrorSocketServer(
    val bindPort: Int = 8787,
    val processStream: InputStream = InputStream.nullInputStream(),
    val processSink: OutputStream = OutputStream.nullOutputStream()
) {
    private val running = AtomicBoolean(false)
    private var serverSocket: ServerSocket? = null
    private val clientSockets = CopyOnWriteArrayList<Socket>()

    fun startListening() {
        if (!running.compareAndSet(false, true)) return

        val server = ServerSocket(bindPort)
        server.reuseAddress = true
        serverSocket = server

        val acceptor = Thread({
            while (running.get() && !server.isClosed) {
                try {
                    val client = server.accept()
                    clientSockets.add(client)
                    spawnClientReader(client)
                } catch (_: Exception) {
                    // Closed or aborted
                }
            }
        }, "atropos-ipc-terminal-acceptor").apply { isDaemon = true; start() }
    }

    private fun spawnClientReader(client: Socket) {
        Thread({
            val input = client.getInputStream()
            val header = ByteArray(5)
            while (running.get() && !client.isClosed) {
                try {
                    var read = 0
                    while (read < 5) {
                        val n = input.read(header, read, 5 - read)
                        if (n < 0) return@Thread
                        read += n
                    }
                    val opcode = header[0]
                    val len = ((header[1].toInt() and 0xFF) shl 24) or
                              ((header[2].toInt() and 0xFF) shl 16) or
                              ((header[3].toInt() and 0xFF) shl 8) or
                              (header[4].toInt() and 0xFF)

                    val payload = ByteArray(len)
                    var payloadRead = 0
                    while (payloadRead < len) {
                        val n = input.read(payload, payloadRead, len - payloadRead)
                        if (n < 0) return@Thread
                        payloadRead += n
                    }

                    when (IpcFrameType.fromOpcode(opcode)) {
                        IpcFrameType.INPUT -> handleClientInput(payload)
                        IpcFrameType.RESIZE -> {
                            if (len >= 8) {
                                val cols = ((payload[0].toInt() and 0xFF) shl 24) or
                                           ((payload[1].toInt() and 0xFF) shl 16) or
                                           ((payload[2].toInt() and 0xFF) shl 8) or
                                           (payload[3].toInt() and 0xFF)
                                val rows = ((payload[4].toInt() and 0xFF) shl 24) or
                                           ((payload[5].toInt() and 0xFF) shl 16) or
                                           ((payload[6].toInt() and 0xFF) shl 8) or
                                           (payload[7].toInt() and 0xFF)
                                handleResizeEvent(TerminalWindowSize(cols, rows))
                            }
                        }
                        IpcFrameType.PING -> {
                            // Respond with pong/ping
                        }
                        else -> {}
                    }
                } catch (_: Exception) {
                    break
                }
            }
            clientSockets.remove(client)
            runCatching { client.close() }
        }, "atropos-ipc-client-reader").apply { isDaemon = true; start() }
    }

    fun broadcastAnsiBytes(bytes: ByteArray, offset: Int, length: Int) {
        if (!running.get() || clientSockets.isEmpty() || length <= 0) return
        val frame = ByteArray(5 + length)
        frame[0] = IpcFrameType.OUTPUT.opcode
        frame[1] = ((length shr 24) and 0xFF).toByte()
        frame[2] = ((length shr 16) and 0xFF).toByte()
        frame[3] = ((length shr 8) and 0xFF).toByte()
        frame[4] = (length and 0xFF).toByte()
        System.arraycopy(bytes, offset, frame, 5, length)

        for (client in clientSockets) {
            try {
                if (!client.isClosed) {
                    val out = client.getOutputStream()
                    out.write(frame)
                    out.flush()
                }
            } catch (_: Exception) {
                clientSockets.remove(client)
                runCatching { client.close() }
            }
        }
    }

    fun handleClientInput(clientInputBytes: ByteArray) {
        try {
            processSink.write(clientInputBytes)
            processSink.flush()
        } catch (_: Exception) {
            // Process sink closed
        }
    }

    fun handleResizeEvent(size: TerminalWindowSize) {
        // Broadcast or pass window resize to PTY session
    }

    fun shutdown() {
        running.set(false)
        for (client in clientSockets) {
            runCatching { client.close() }
        }
        clientSockets.clear()
        runCatching { serverSocket?.close() }
    }
}
