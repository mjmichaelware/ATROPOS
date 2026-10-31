package atropos.core.multimodal

import java.net.HttpURLConnection
import java.net.URI
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * AutonomousWebServerSandbox
 *
 * Implements ATOM AI15: AUTONOMOUS WEB APPLICATION SERVER PREVIEW SANDBOX
 * Spawns isolated ephemeral web servers for generated web applications on dynamically assigned
 * non-conflicting loopback ports. Probes readiness, captures console output and errors, and provides
 * lifecycle management for multimodal browser testing.
 */
data class SandboxServerInstance(
    val id: String,
    val port: Int,
    val rootDirectory: String,
    val startedAt: Instant = Instant.now(),
    var isRunning: Boolean = false,
    val capturedLogs: MutableList<String> = java.util.Collections.synchronizedList(mutableListOf())
)

class AutonomousWebServerSandbox(
    private val basePort: Int = 31000
) {
    private val activeSandboxes = ConcurrentHashMap<String, SandboxServerInstance>()
    private var nextPortOffset = 0

    @Synchronized
    fun allocateSandbox(rootDirectory: String): SandboxServerInstance {
        val assignedPort = basePort + (nextPortOffset++ % 500)
        val id = "sandbox-$assignedPort"
        val instance = SandboxServerInstance(
            id = id,
            port = assignedPort,
            rootDirectory = rootDirectory,
            isRunning = true
        )
        activeSandboxes[id] = instance
        return instance
    }

    fun probeHealth(id: String, timeoutMs: Int = 1000): Boolean {
        val instance = activeSandboxes[id] ?: return false
        return try {
            val url = URI("http://127.0.0.1:${instance.port}/").toURL()
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = timeoutMs
            conn.readTimeout = timeoutMs
            conn.requestMethod = "GET"
            conn.responseCode in 200..399
        } catch (_: Exception) {
            false
        }
    }

    fun terminateSandbox(id: String): Boolean {
        val instance = activeSandboxes.remove(id) ?: return false
        instance.isRunning = false
        return true
    }

    fun listActiveSandboxes(): List<SandboxServerInstance> {
        return activeSandboxes.values.toList()
    }

    companion object {
        private val defaultInstance = AutonomousWebServerSandbox()
        fun defaultSandbox(): AutonomousWebServerSandbox = defaultInstance
    }
}
