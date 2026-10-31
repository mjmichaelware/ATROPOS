/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.bridge

import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * ATOM 21.3: Real-Time Engine Execution Telemetry Return.
 *
 * Streams live execution progress, active queue states, and tool outcomes back
 * to mobile and web clients without manual reloads or aggressive polling.
 *
 * Research Directive: Server-Sent Events (SSE) backpressure mitigation strategies
 * to throttle high-frequency log updates gracefully when client rendering buffers lag.
 */
class DecoupledStreamTelemetryHub(
    private val maxEvents: Int = 1000,
    private val sseBackpressureThresholdMs: Long = 100L
) {
    data class StreamTelemetryEvent(
        val cursor: Long,
        val type: String,
        val timestamp: Instant,
        val detail: String,
        val backpressureThrottled: Boolean = false
    )

    private val eventLog = ArrayList<StreamTelemetryEvent>()
    private val sequence = AtomicLong(0)
    private val lock = Any()
    private val clientLastAckTimestamps = ConcurrentHashMap<String, Instant>()

    fun emitEvent(type: String, detail: String): StreamTelemetryEvent = synchronized(lock) {
        val cursor = sequence.incrementAndGet()
        val event = StreamTelemetryEvent(
            cursor = cursor,
            type = type,
            timestamp = Instant.now(),
            detail = detail
        )
        eventLog.add(event)
        while (eventLog.size > maxEvents) {
            eventLog.removeAt(0)
        }
        event
    }

    fun pollEventsAfter(cursor: Long, clientId: String? = null): List<StreamTelemetryEvent> = synchronized(lock) {
        if (clientId != null) {
            clientLastAckTimestamps[clientId] = Instant.now()
        }
        eventLog.filter { it.cursor > cursor }
    }

    fun shouldThrottleClient(clientId: String): Boolean {
        val lastAck = clientLastAckTimestamps[clientId] ?: return false
        val lagMs = Instant.now().toEpochMilli() - lastAck.toEpochMilli()
        return lagMs > (sseBackpressureThresholdMs * 10)
    }
}
