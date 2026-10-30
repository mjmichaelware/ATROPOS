/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.android.bridge

import atropos.cli.ui.EngineSixAnswers
import atropos.core.AtroposConfig
import atropos.core.agent.AgentService

/**
 * Direct in-process engine bridge for Android module.
 *
 * Guarantees fresh initialization launches with a null active project and empty state indicators.
 * Never preloads development repositories, mock personal artifacts, or pre-seeded credentials.
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

    fun submitGoal(text: String): Boolean {
        if (text.isBlank() || agentService == null) return false
        answers = EngineSixAnswers(
            problem = text.trim(),
            approach = "Pending engine goal orchestration",
            invariants = "Axiomatic isolation & soft-fail networking",
            changes = "Unapplied",
            verification = "Untested",
            confidence = 1.0
        )
        return true
    }

    fun setActiveProject(projectName: String?) {
        this.activeProjectName = projectName
    }

    fun updateAnswers(newAnswers: EngineSixAnswers) {
        this.answers = newAnswers
    }

    fun resetState() {
        this.activeProjectName = null
        this.answers = EngineSixAnswers()
    }
}
