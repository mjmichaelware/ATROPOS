/* SPDX-License-Identifier: AGPL-3.0-only */
/**
 * B-SUP-036: Regret Minimization Dashboard
 *
 * Implements regret minimization tracking and dashboard
 * for monitoring decision quality over time.
 */
package atropos.core.supremacy

import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant

object RegretMinimization {
    data class Decision(
        val id: String = java.util.UUID.randomUUID().toString(),
        val action: String,
        val expectedUtility: Double,
        val actualUtility: Double?,
        val context: Map<String, String> = emptyMap(),
        val timestamp: Instant = Instant.now()
    ) {
        val regret: Double? = actualUtility?.let { expectedUtility - it }
    }

    private val decisions = mutableListOf<Decision>()

    /**
     * Records a decision.
     */
    fun record(action: String, expectedUtility: Double, context: Map<String, String> = emptyMap()): Decision {
        val decision = Decision(action = action, expectedUtility = expectedUtility, context = context)
        decisions.add(decision)
        return decision
    }

    /**
     * Updates actual utility for a decision.
     */
    fun updateActualUtility(decisionId: String, actualUtility: Double): Boolean {
        val index = decisions.indexOfFirst { it.id == decisionId }
        if (index < 0) return false
        val decision = decisions[index]
        decisions[index] = decision.copy(actualUtility = actualUtility)
        return true
    }

    /**
     * Computes cumulative regret.
     */
    fun cumulativeRegret(): Double {
        return decisions.mapNotNull { it.regret }.sum()
    }

    /**
     * Computes average regret per decision.
     */
    fun averageRegret(): Double {
        val regrets = decisions.mapNotNull { it.regret }
        return if (regrets.isEmpty()) 0.0 else regrets.average()
    }

    /**
     * Gets regret by action type.
     */
    fun regretByAction(): Map<String, Double> {
        return decisions
            .filter { it.actualUtility != null }
            .groupBy { it.action }
            .mapValues { (_, ds) -> ds.mapNotNull { it.regret }.average() }
    }

    /**
     * Persists decisions to CAS.
     */
    fun persistDecisions(casDir: Path): Path {
        Files.createDirectories(casDir)
        val file = casDir.resolve("regret-decisions.json")
        val json = decisions.map { d ->
            """
                {
                    "id": "${d.id}",
                    "action": "${d.action}",
                    "expectedUtility": ${d.expectedUtility},
                    "actualUtility": ${d.actualUtility ?: "null"},
                    "regret": ${d.regret ?: "null"},
                    "timestamp": "${d.timestamp}"
                }
            """.trimIndent()
        }.joinToString(",\n", "[\n", "\n]")
        Files.writeString(file, json)
        return file
    }

    /**
     * CLI command to show regret dashboard.
     */
    fun showDashboard(): String {
        return """
            Regret Minimization Dashboard:
              Total Decisions: ${decisions.size}
              Cumulative Regret: ${"%.4f".format(cumulativeRegret())}
              Average Regret: ${"%.4f".format(averageRegret())}
              By Action:
        ${regretByAction().joinToString("\n") { "  $it.key: ${"%.4f".format(it.value)}" }}
        """.trimIndent()
    }
}