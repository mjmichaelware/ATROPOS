/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.shared

/**
 * Platform-neutral state vocabulary shared by JVM, Android, and future iOS
 * adapters. Policy and persistence remain in the engine; this module only
 * carries serializable state transitions across platform boundaries.
 */
enum class PortableRunStatus {
    IDLE,
    PLANNING,
    WAITING,
    WORKING,
    REVIEW_REQUIRED,
    BLOCKED,
    COMPLETED,
    FAILED,
    CANCELLED
}

data class PortableEngineState(
    val projectId: String,
    val runId: String,
    val status: PortableRunStatus,
    val activeProvider: String?,
    val checkpointId: String?
)

sealed class PortableRunEvent {
    data object BeginPlanning : PortableRunEvent()
    data object BeginWork : PortableRunEvent()
    data object RequireReview : PortableRunEvent()
    data class Finish(val success: Boolean) : PortableRunEvent()
    data object Cancel : PortableRunEvent()
}

/** Deterministic reducer; adapters render its result but never own policy. */
object PortableEngineReducer {
    fun reduce(state: PortableEngineState, event: PortableRunEvent): PortableEngineState {
        val next = when (event) {
            PortableRunEvent.BeginPlanning -> PortableRunStatus.PLANNING
            PortableRunEvent.BeginWork -> PortableRunStatus.WORKING
            PortableRunEvent.RequireReview -> PortableRunStatus.REVIEW_REQUIRED
            is PortableRunEvent.Finish -> if (event.success) PortableRunStatus.COMPLETED else PortableRunStatus.FAILED
            PortableRunEvent.Cancel -> PortableRunStatus.CANCELLED
        }
        return state.copy(status = next)
    }
}
