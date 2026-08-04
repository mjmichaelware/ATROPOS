/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.interrupt

import java.time.Instant
import java.util.concurrent.atomic.AtomicReference

/**
 * The interrupt state a running loop cooperates with.
 *
 * Deliberately cooperative rather than pre-emptive. A run holds territory,
 * leases and a half-written DAG position; killing its thread from outside would
 * leave all three in whatever state they happened to be in, which is the
 * process-death outcome this atom exists to replace. Instead the loop asks
 * [shouldStop] at its own boundaries — the points where its state is already
 * consistent — so a stop is always taken somewhere it can be resumed from.
 *
 * That means an interrupt is a *request* until the loop honours it. [requested]
 * and [taken] are separate for exactly that reason: an operator who pressed
 * freeze needs to know the difference between "asked" and "the run has actually
 * stopped", and collapsing them would report a still-running job as frozen.
 */
class InterruptController(
    private val clock: () -> Instant = { Instant.now() }
) {
    private val stateRef = AtomicReference(InterruptState())

    fun state(): InterruptState = stateRef.get()

    /**
     * Records an interrupt request.
     *
     * A stronger level supersedes a weaker one already pending: an operator who
     * pressed soft and then hard has changed their mind toward stopping sooner,
     * and honouring the earlier, gentler request would ignore the later one.
     * The reverse is refused for the same reason.
     */
    fun request(level: InterruptLevel, requestedBy: String): InterruptState =
        stateRef.updateAndGet { current ->
            val pending = current.requested
            if (pending != null && severity(pending.level) >= severity(level)) current
            else current.copy(
                requested = InterruptRequest(level, requestedBy, clock())
            )
        }

    /** True at a loop boundary when an interrupt is pending and not yet taken. */
    fun shouldStop(): Boolean = state().let { it.requested != null && it.taken == null }

    /**
     * Marks the pending interrupt as actually taken by the loop.
     *
     * [resumePoint] is required for a resumable level and refused for [HARD]:
     * a hard stop has no consistent position to resume from, and recording one
     * would invite a resume that silently restarts mid-step.
     */
    fun take(resumePoint: String?): InterruptOutcome {
        val current = state()
        val request = current.requested
            ?: return InterruptOutcome.Refused("no interrupt was requested")
        if (current.taken != null) {
            return InterruptOutcome.Refused("interrupt was already taken")
        }
        if (request.level.resumable && resumePoint.isNullOrBlank()) {
            return InterruptOutcome.Refused(
                "${request.level.canonical} interrupt is resumable and requires a resume point"
            )
        }
        val taken = InterruptTaken(
            level = request.level,
            resumePoint = if (request.level.resumable) resumePoint else null,
            takenAt = clock()
        )
        stateRef.updateAndGet { it.copy(taken = taken) }
        return InterruptOutcome.Taken(taken)
    }

    /** Clears the interrupt so a resumed run is not immediately stopped again. */
    fun clear() {
        stateRef.set(InterruptState())
    }

    private fun severity(level: InterruptLevel): Int = when (level) {
        InterruptLevel.SOFT -> 1
        InterruptLevel.FREEZE -> 2
        InterruptLevel.HARD -> 3
    }
}

data class InterruptRequest(
    val level: InterruptLevel,
    val requestedBy: String,
    val requestedAt: Instant
)

data class InterruptTaken(
    val level: InterruptLevel,
    /** Null for a non-resumable stop; never a fabricated position. */
    val resumePoint: String?,
    val takenAt: Instant
)

data class InterruptState(
    val requested: InterruptRequest? = null,
    val taken: InterruptTaken? = null
) {
    /** True only once the loop has actually stopped, not when asked to. */
    val isStopped: Boolean get() = taken != null

    val isPending: Boolean get() = requested != null && taken == null

    fun render(): String = when {
        taken != null ->
            "interrupt ${taken.level.canonical} taken at ${taken.takenAt}" +
                (taken.resumePoint?.let { " resume=$it" } ?: " (not resumable)")
        requested != null ->
            "interrupt ${requested.level.canonical} requested by ${requested.requestedBy}; " +
                "the run has not stopped yet"
        else -> "no interrupt"
    }
}

sealed class InterruptOutcome {
    data class Taken(val taken: InterruptTaken) : InterruptOutcome()
    data class Refused(val reason: String) : InterruptOutcome()
}
