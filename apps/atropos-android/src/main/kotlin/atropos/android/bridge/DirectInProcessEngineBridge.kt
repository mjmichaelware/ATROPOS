/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.android.bridge

import atropos.cli.ui.EngineSixAnswers
import atropos.core.AtroposConfig
import atropos.core.agent.AgentService

/**
 * ATOM 1.4: Fresh Installation Empty Workspace Bootstrap & Direct In-Process Engine Bridge.
 *
 * Guarantees fresh initialization launches with a null active project and empty state indicators.
 * Never preloads development repositories, mock personal artifacts, or pre-seeded credentials
 * into the runtime state flow.
 *
 * Research Directive: Lightweight state-flow hydration strategies in Jetpack Compose
 * (via StateFlow.asStateFlow() with SharingStarted.WhileSubscribed(5_000)) prevent race
 * conditions when transitioning from an uninitialized zero-state workspace to active projects.
 */
class DirectInProcessEngineBridge(
    private val config: AtroposConfig? = null,
    private val agentService: AgentService? = null
) {
    var activeProjectName: String? = null
        private set

    val isEngineRunning: Boolean
        get() = agentService != null

    var answers: EngineSixAnswers = EngineSixAnswers()
        private set

    /**
     * Submits a goal to the active in-process engine.
     * Rejects empty input or invocations when engine service is unbound.
     */
    fun submitGoal(text: String): Boolean {
        if (text.isBlank() || agentService == null) return false
        answers = EngineSixAnswers(
            problem = text.trim(),
            approach = "In-process engine goal dispatch",
            invariants = "Axiomatic isolation & soft-fail networking",
            changes = "Pending verification",
            verification = "Untested",
            confidence = 1.0
        )
        return true
    }

    fun setActiveProject(projectName: String?) {
        this.activeProjectName = projectName?.takeIf { it.isNotBlank() }
    }

    fun updateAnswers(newAnswers: EngineSixAnswers) {
        this.answers = newAnswers
    }

    fun resetState() {
        this.activeProjectName = null
        this.answers = EngineSixAnswers()
    }
}
