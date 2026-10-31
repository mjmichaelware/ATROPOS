/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.agent

import java.time.Duration
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * ATOM CC1: CLOUD WORKSPACE INACTIVITY SUSPENSION MONITOR
 * Path: src/main/kotlin/atropos/core/agent/CloudWorkspaceInactivityMonitor.kt
 *
 * Monitors operator interactivity and running task queues. Automatically triggers
 * container suspension or resource scaling when inactivity exceeds the configured
 * threshold, preserving workspace disk state while eliminating idle compute costs.
 */
data class InactivityMonitorConfig(
    val idleTimeoutMinutes: Long = 30L,
    val pollIntervalSeconds: Long = 15L,
    val autoSuspendEnabled: Boolean = true
)

enum class SuspensionState {
    ACTIVE,
    IDLE_WARNING,
    SUSPENDING,
    SUSPENDED
}

data class WorkspaceActivitySnapshot(
    val lastOperatorInteraction: Instant,
    val activeTaskCount: Int,
    val state: SuspensionState,
    val idleDuration: Duration,
    val timeUntilSuspension: Duration
)

class CloudWorkspaceInactivityMonitor(
    private val config: InactivityMonitorConfig = InactivityMonitorConfig(),
    private val clock: () -> Instant = { Instant.now() },
    private val suspendAction: (suspendReason: String) -> Boolean = { true }
) {
    private val lastInteractionTime = AtomicReference<Instant>(clock())
    private val currentActiveTasks = AtomicReference<Int>(0)
    private val isSuspended = AtomicBoolean(false)

    fun recordHeartbeat(operatorActive: Boolean = true) {
        if (operatorActive) {
            lastInteractionTime.set(clock())
            if (isSuspended.get()) {
                isSuspended.set(false)
            }
        }
    }

    fun updateTaskCount(activeTasks: Int) {
        currentActiveTasks.set(activeTasks.coerceAtLeast(0))
        if (activeTasks > 0) {
            lastInteractionTime.set(clock())
        }
    }

    fun evaluateActivity(): WorkspaceActivitySnapshot {
        val now = clock()
        val lastInteraction = lastInteractionTime.get()
        val idleDuration = Duration.between(lastInteraction, now)
        val timeoutDuration = Duration.ofMinutes(config.idleTimeoutMinutes)
        val activeTasks = currentActiveTasks.get()

        val state = when {
            isSuspended.get() -> SuspensionState.SUSPENDED
            activeTasks > 0 -> SuspensionState.ACTIVE
            idleDuration >= timeoutDuration && config.autoSuspendEnabled -> {
                val success = suspendAction("Idle timeout exceeded (${config.idleTimeoutMinutes} minutes)")
                if (success) {
                    isSuspended.set(true)
                    SuspensionState.SUSPENDED
                } else {
                    SuspensionState.SUSPENDING
                }
            }
            idleDuration >= timeoutDuration.minusMinutes(5) -> SuspensionState.IDLE_WARNING
            else -> SuspensionState.ACTIVE
        }

        val remaining = if (idleDuration < timeoutDuration) {
            timeoutDuration.minus(idleDuration)
        } else {
            Duration.ZERO
        }

        return WorkspaceActivitySnapshot(
            lastOperatorInteraction = lastInteraction,
            activeTaskCount = activeTasks,
            state = state,
            idleDuration = idleDuration,
            timeUntilSuspension = remaining
        )
    }

    fun isStale(): Boolean {
        val now = clock()
        val idle = Duration.between(lastInteractionTime.get(), now)
        return currentActiveTasks.get() == 0 && idle >= Duration.ofMinutes(config.idleTimeoutMinutes)
    }

    companion object {
        fun defaultMonitor(): CloudWorkspaceInactivityMonitor = CloudWorkspaceInactivityMonitor()
    }
}
