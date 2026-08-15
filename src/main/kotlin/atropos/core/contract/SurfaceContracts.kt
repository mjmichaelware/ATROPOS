/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.contract

class SimpleStateFlow<S>(var value: S)

/** MVI core contracts for all 16 ATROPOS views. */
interface MviState
interface MviIntent
interface MviReducer<S : MviState, I : MviIntent> {
    fun reduce(currentState: S, intent: I): S
}

class ViewStateManager<S : MviState, I : MviIntent>(
    initialState: S,
    private val reducer: MviReducer<S, I>
) {
    val state = SimpleStateFlow(initialState)

    fun dispatch(intent: I) {
        state.value = reducer.reduce(state.value, intent)
    }
}

/** Concentricity and tap target design tokens (Items 139–141). */
object UiDesignTokens {
    fun parentRadiusMinusInset(parentRadius: Double, inset: Double): Double = parentRadius - inset
    const val MINIMUM_TAP_TARGET_PT = 44.0
    const val TINTED_THEME_VARIANT = "TINTED"
}

/** The 16 canonical views defined in Sections B/D/E. */
enum class AtroposView {
    GOVERNANCE,
    PIPELINE_ARTIFACTS,
    DAG,
    SNAPSHOTS,
    SECURITY,
    MEMORY,
    PAID_EMERGENCY,
    VERIFY,
    SOURCE_LOOKUP,
    AUTONOMOUS,
    SWARM,
    JOBS_QUEUE,
    PLATFORM,
    CONVERSATION,
    TIMELINE,
    EXECUTION_MONITOR
}
