/* SPDX-License-Identifier: AGPL-3.0-only */
/**
 * B-SUP-020: Coalitional Stability Check
 *
 * Implements stability checks for agent coalitions to ensure
 * that agent groups remain stable and don't fragment.
 */
package atropos.core.supremacy

import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant

object CoalitionalStability {
    data class Coalition(
        val id: String,
        val members: Set<String>,
        val purpose: String,
        val formedAt: Instant = Instant.now(),
        val stabilityScore: Double = 1.0
    ) {
        val size: Int = members.size
    }

    data class StabilityReport(
        val coalitionId: String,
        val score: Double,
        val factors: Map<String, Double>,
        val timestamp: Instant = Instant.now()
    ) {
        val isStable: Boolean = score >= 0.7
    }

    private val coalitions = mutableMapOf<String, Coalition>()

    /**
     * Creates a new coalition.
     */
    fun form(members: Set<String>, purpose: String): Coalition {
        val coalition = Coalition(
            id = java.util.UUID.randomUUID().toString(),
            members = members,
            purpose = purpose
        )
        coalitions[coalition.id] = coalition
        return coalition
    }

    /**
     * Adds a member to a coalition.
     */
    fun addMember(coalitionId: String, member: String): Boolean {
        val coalition = coalitions[coalitionId] ?: return false
        coalitions[coalitionId] = coalition.copy(members = coalition.members + member)
        return true
    }

    /**
     * Removes a member from a coalition.
     */
    fun removeMember(coalitionId: String, member: String): Boolean {
        val coalition = coalitions[coalitionId] ?: return false
        coalitions[coalitionId] = coalition.copy(members = coalition.members - member)
        return true
    }

    /**
     * Computes stability score for a coalition.
     */
    fun checkStability(coalitionId: String): StabilityReport {
        val coalition = coalitions[coalitionId] ?: return StabilityReport(coalitionId, 0.0, emptyMap())

        val factors = mutableMapOf<String, Double>()
        var score = 1.0

        // Size factor: larger coalitions more stable up to a point
        val sizeFactor = when {
            coalition.size <= 2 -> 0.5
            coalition.size <= 5 -> 0.8
            coalition.size <= 10 -> 1.0
            else -> 0.9
        }
        factors["size"] = sizeFactor
        score *= sizeFactor

        // Age factor: older coalitions more stable
        val ageHours = (Instant.now().toEpochMilli() - coalition.formedAt.toEpochMilli()) / 3600000.0
        val ageFactor = when {
            ageHours < 1 -> 0.7
            ageHours < 24 -> 0.85
            ageHours < 168 -> 0.95
            else -> 1.0
        }
        factors["age"] = ageFactor
        score *= ageFactor

        // Purpose alignment (simplified)
        factors["purpose"] = 1.0

        return StabilityReport(coalition.id, score.coerceIn(0.0, 1.0), factors.toMap())
    }

    /**
     * Persists coalitions to CAS.
     */
    fun persistCoalitions(casDir: Path): Path {
        Files.createDirectories(casDir)
        val file = casDir.resolve("coalitions.json")
        val json = coalitions.values.map { c ->
            """
                {
                    "id": "${c.id}",
                    "members": [${c.members.joinToString(", ") { "\"$it\"" }}],
                    "purpose": "${c.purpose}",
                    "formedAt": "${c.formedAt}",
                    "stabilityScore": ${c.stabilityScore}
                }
            """.trimIndent()
        }.joinToString(",\n", "[\n", "\n]")
        Files.writeString(file, json)
        return file
    }

    /**
     * CLI command to show stability.
     */
    fun showStability(): String {
        return coalitions.values.map { c ->
            val report = checkStability(c.id)
            "${c.id} (${c.members.size} members): ${if (report.isStable) "STABLE" else "UNSTABLE"} (${"%.2f".format(report.score)})"
        }.joinToString("\n")
    }
}