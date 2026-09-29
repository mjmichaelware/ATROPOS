/* SPDX-License-Identifier: AGPL-3.0-only */
/**
 * B-SUP-019: Sub-Agent Incentive Mechanism
 *
 * Implements incentive mechanisms for sub-agents to encourage
 * desired behaviors and discourage adversarial actions.
 */
package atropos.core.supremacy

import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant

object SubAgentIncentives {
    data class Incentive(
        val agentId: String,
        val reward: Double,
        val reason: String,
        val timestamp: Instant = Instant.now()
    )

    data class Penalty(
        val agentId: String,
        val cost: Double,
        val reason: String,
        val timestamp: Instant = Instant.now()
    )

    data class AgentState(
        val agentId: String,
        val totalRewards: Double = 0.0,
        val totalPenalties: Double = 0.0,
        val reputation: Double = 1.0,
        val lastUpdate: Instant = Instant.now()
    ) {
        val netScore: Double = totalRewards - totalPenalties
        val adjustedReputation: Double = reputation * (1.0 + netScore / 100.0)
    }

    private val agentStates = mutableMapOf<String, AgentState>()
    private val incentives = mutableListOf<Incentive>()
    private val penalties = mutableListOf<Penalty>()

    /**
     * Rewards an agent for desired behavior.
     */
    fun reward(agentId: String, amount: Double, reason: String) {
        val state = agentStates.getOrPut(agentId) { AgentState(agentId) }
        agentStates[agentId] = state.copy(
            totalRewards = state.totalRewards + amount,
            lastUpdate = Instant.now()
        )
        incentives.add(Incentive(agentId, amount, reason))
    }

    /**
     * Penalizes an agent for undesired behavior.
     */
    fun penalize(agentId: String, amount: Double, reason: String) {
        val state = agentStates.getOrPut(agentId) { AgentState(agentId) }
        agentStates[agentId] = state.copy(
            totalPenalties = state.totalPenalties + amount,
            reputation = (state.reputation * 0.9).coerceAtLeast(0.1),
            lastUpdate = Instant.now()
        )
        penalties.add(Penalty(agentId, amount, reason))
    }

    /**
     * Gets the current state of an agent.
     */
    fun getState(agentId: String): AgentState = agentStates.getOrPut(agentId) { AgentState(agentId) }

    /**
     * Gets the leaderboard of agents by net score.
     */
    fun leaderboard(): List<AgentState> {
        return agentStates.values.sortedByDescending { it.netScore }
    }

    /**
     * Persists incentive state to CAS.
     */
    fun persistState(casDir: Path): Path {
        Files.createDirectories(casDir)
        val file = casDir.resolve("sub-agent-incentives.json")
        val json = agentStates.values.map { s ->
            """
                {
                    "agentId": "${s.agentId}",
                    "totalRewards": ${s.totalRewards},
                    "totalPenalties": ${s.totalPenalties},
                    "reputation": ${s.reputation},
                    "netScore": ${s.netScore}
                }
            """.trimIndent()
        }.joinToString(",\n", "[\n", "\n]")
        Files.writeString(file, json)
        return file
    }

    /**
     * CLI command to show leaderboard.
     */
    fun showLeaderboard(): String {
        return leaderboard().joinToString("\n") { "${it.agentId}: net=${"%.2f".format(it.netScore)} rep=${"%.2f".format(it.reputation)}" }
    }
}