/* SPDX-License-Identifier: AGPL-3.0-only */
/**
 * B-SUP-035: Live Competitive Shadow Mode
 *
 * Implements shadow mode where ATROPOS runs alongside
 * another agent and compares decisions.
 */
package atropos.core.supremacy

import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant

object ShadowMode {
    data class Comparison(
        val id: String = java.util.UUID.randomUUID().toString(),
        val input: String,
        val atroposOutput: String,
        val competitorOutput: String,
        val winner: Winner,
        val timestamp: Instant = Instant.now()
    ) {
        enum class Winner { ATROPOS, COMPETITOR, TIE, ERROR }
    }

    private val comparisons = mutableListOf<Comparison>()

    /**
     * Records a shadow comparison.
     */
    fun compare(
        input: String,
        atroposOutput: String,
        competitorOutput: String,
        judge: (String, String, String) -> Comparison.Winner
    ): Comparison {
        val winner = judge(input, atroposOutput, competitorOutput)
        val comparison = Comparison(
            input = input,
            atroposOutput = atroposOutput,
            competitorOutput = competitorOutput,
            winner = winner
        )
        comparisons.add(comparison)
        return comparison
    }

    /**
     * Gets win rate against a competitor.
     */
    fun winRate(): Double {
        if (comparisons.isEmpty()) return 0.0
        val wins = comparisons.count { it.winner == Comparison.Winner.ATROPOS }
        return wins.toDouble() / comparisons.size
    }

    /**
     * Gets tie rate.
     */
    fun tieRate(): Double {
        if (comparisons.isEmpty()) return 0.0
        return comparisons.count { it.winner == Comparison.Winner.TIE }.toDouble() / comparisons.size
    }

    /**
     * Persists shadow comparisons to CAS.
     */
    fun persistComparisons(casDir: Path): Path {
        Files.createDirectories(casDir)
        val file = casDir.resolve("shadow-comparisons.json")
        val json = comparisons.map { c ->
            """
                {
                    "id": "${c.id}",
                    "input": "${c.input.replace("\"", "\\\"")}",
                    "atroposOutput": "${c.atroposOutput.replace("\"", "\\\"")}",
                    "competitorOutput": "${c.competitorOutput.replace("\"", "\\\"")}",
                    "winner": "${c.winner}",
                    "timestamp": "${c.timestamp}"
                }
            """.trimIndent()
        }.joinToString(",\n", "[\n", "\n]")
        Files.writeString(file, json)
        return file
    }

    /**
     * CLI command to show shadow mode stats.
     */
    fun showStats(): String {
        val total = comparisons.size
        val wins = comparisons.count { it.winner == Comparison.Winner.ATROPOS }
        val ties = comparisons.count { it.winner == Comparison.Winner.TIE }
        val losses = comparisons.count { it.winner == Comparison.Winner.COMPETITOR }
        return """
            Shadow Mode Stats:
              Total: $total
              Wins: $wins (${"%.1f".format(wins.toDouble() / total * 100)}%)
              Ties: $ties (${"%.1f".format(ties.toDouble() / total * 100)}%)
              Losses: $losses (${"%.1f".format(losses.toDouble() / total * 100)}%)
              Win Rate: ${"%.1f".format(winRate() * 100)}%
        """.trimIndent()
    }
}