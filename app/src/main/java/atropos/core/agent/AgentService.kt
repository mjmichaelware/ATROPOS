/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.agent

/**
 * Android client contract binding for AgentService.
 */
interface AgentService {
    fun submitGoal(goal: String): Boolean = true
}
