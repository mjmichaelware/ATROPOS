/* SPDX-License-Identifier: AGPL-3.0-only */
/**
 * B-SUP-023: Negative-Result Memory
 *
 * Implements memory for negative results to prevent
 * repeating failed experiments.
 */
package atropos.core.supremacy

import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant

object NegativeResultMemory {
    data class NegativeResult(
        val id: String = java.util.UUID.randomUUID().toString(),
        val experiment: String,
        val hypothesis: String,
        val reason: String,
        val context: Map<String, String> = emptyMap(),
        val recordedBy: String,
        val recordedAt: Instant = Instant.now()
    )

    private val negativeResults = mutableListOf<NegativeResult>()

    /**
     * Records a negative result.
     */
    fun record(
        experiment: String,
        hypothesis: String,
        reason: String,
        context: Map<String, String> = emptyMap(),
        recordedBy: String
    ): NegativeResult {
        val result = NegativeResult(
            experiment = experiment,
            hypothesis = hypothesis,
            reason = reason,
            context = context,
            recordedBy = recordedBy
        )
        negativeResults.add(result)
        return result
    }

    /**
     * Checks if an experiment has been tried before with negative result.
     */
    fun hasNegativeResult(experiment: String, hypothesis: String): Boolean {
        return negativeResults.any { it.experiment == experiment && it.hypothesis == hypothesis }
    }

    /**
     * Gets all negative results for an experiment.
     */
    fun getForExperiment(experiment: String): List<NegativeResult> {
        return negativeResults.filter { it.experiment == experiment }
    }

    /**
     * Gets all negative results for a hypothesis.
     */
    fun getForHypothesis(hypothesis: String): List<NegativeResult> {
        return negativeResults.filter { it.hypothesis == hypothesis }
    }

    /**
     * Persists negative results to CAS.
     */
    fun persistMemory(casDir: Path): Path {
        Files.createDirectories(casDir)
        val file = casDir.resolve("negative-results.json")
        val json = negativeResults.map { r ->
            """
                {
                    "id": "${r.id}",
                    "experiment": "${r.experiment}",
                    "hypothesis": "${r.hypothesis}",
                    "reason": "${r.reason}",
                    "context": ${r.context.joinToString(",") { "\"$it.key\": \"$it.value\"" }},
                    "recordedBy": "${r.recordedBy}",
                    "recordedAt": "${r.recordedAt}"
                }
            """.trimIndent()
        }.joinToString(",\n", "[\n", "\n]")
        Files.writeString(file, json)
        return file
    }

    /**
     * CLI command to show negative results.
     */
    fun showMemory(): String {
        return negativeResults.joinToString("\n") { "  ${it.experiment}: ${it.reason}" }
    }
}