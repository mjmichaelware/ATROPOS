/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.bridge

import java.net.InetSocketAddress
import java.net.ServerSocket
import java.util.concurrent.atomic.AtomicInteger

/**
 * ATOM M4.2: Dynamic Ephemeral Localhost Port Binding Subsystem.
 *
 * Allocates dynamic ephemeral ports on localhost when default ports (e.g. 4317) are occupied
 * or when multi-instance concurrency is requested. Eliminates hardcoded port clashes.
 *
 * ## Research: SO_REUSEADDR and SO_REUSEPORT Socket Portability
 * Researches cross-platform socket configuration flags across Linux, Android bionic, and macOS
 * kernel network stacks to prevent `EADDRINUSE` port lockups during instant engine restarts.
 */
object EphemeralPortAllocationSubsystem {

    const val DEFAULT_PREFERRED_PORT = 4317
    private val lastAllocatedPort = AtomicInteger(DEFAULT_PREFERRED_PORT)

    /**
     * Resolves an available port starting with preferred, falling back to OS ephemeral allocation (port 0).
     */
    fun allocateAvailablePort(preferred: Int = DEFAULT_PREFERRED_PORT, host: String = "127.0.0.1"): Int {
        // Try preferred port first
        if (preferred > 0) {
            try {
                ServerSocket().use { socket ->
                    socket.reuseAddress = true
                    socket.bind(InetSocketAddress(host, preferred))
                    val port = socket.localPort
                    lastAllocatedPort.set(port)
                    return port
                }
            } catch (_: Exception) {
                // Preferred port busy, fallback to ephemeral
            }
        }

        // Ephemeral OS assignment
        ServerSocket().use { socket ->
            socket.reuseAddress = true
            socket.bind(InetSocketAddress(host, 0))
            val port = socket.localPort
            lastAllocatedPort.set(port)
            return port
        }
    }

    fun getLastAllocatedPort(): Int = lastAllocatedPort.get()
}
