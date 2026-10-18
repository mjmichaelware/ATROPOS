/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.android.bridge

import atropos.core.AtroposConfig
import atropos.core.agent.AgentService

/**
 * Direct in-process engine bridge for Android module.
 */
class DirectInProcessEngineBridge(
    private val config: AtroposConfig? = null,
    private val agentService: AgentService? = null
) {
    fun submitGoal(text: String): Boolean {
        return agentService?.submitGoal(text) ?: false
    }
}
