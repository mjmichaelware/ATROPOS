/* SPDX-License-Identifier: AGPL-3.0-only */
/**
 * B-SUP-025: Counterfactual Replay
 *
 * Implements counterfactual replay to answer "what if" questions
 * about alternative execution paths.
 */
package atropos.core.supremacy

import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant

object CounterfactualReplay {
    data class Scenario(
        val id: String = java.util.UUID.randomUUID().toString(),
        val name: String,
        val baseState: String,           // Serialized state at decision point
        val intervention: String,        // What to change
        val predictedOutcome: String?,   // Expected outcome
        val actualOutcome: String? = null,
        val status: Status = Status.PENDING
    ) {
        enum class Status { PENDING, RUNNING, COMPLETED, FAILED }
    }

    data class ReplayResult(
        val scenarioId: String,
        val outcome: String,
        val divergencePoint: String,
        val metrics: Map<String, Double> = emptyMap()
    )

    private val scenarios = mutableMapOf<String, Scenario>()
    private val results = mutableListOf<ReplayResult>()

    /**
     * Creates a counterfactual scenario.
     */
    fun createScenario(
        name: String,
        baseState: String,
        intervention: String,
        predictedOutcome: String? = null
    ): Scenario {
        val scenario = Scenario(name = name, baseState = baseState, intervention = intervention, predictedOutcome = predictedOutcome)
        scenarios[scenario.id] = scenario
        return scenario
    }

    /**
     * Runs a counterfactual replay.
     */
    fun run(scenarioId: String, executor: (String, String) -> String): ReplayResult {
        val scenario = scenarios[scenarioId] ?: throw IllegalArgumentException("Scenario not found: $scenarioId")
        scenarios[scenarioId] = scenario.copy(status = Scenario.Status.RUNNING)

        try {
            val outcome = executor(scenario.baseState, scenario.intervention)
            val result = ReplayResult(
                scenarioId = scenarioId,
                outcome = outcome,
                divergencePoint = scenario.intervention,
                metrics = mapOf("divergence" to 1.0)
            )
            scenarios[scenarioId] = scenario.copy(status = Scenario.Status.COMPLETED, actualOutcome = outcome)
            results.add(result)
            return result
        } catch (e: Exception) {
            scenarios[scenarioId] = scenario.copy(status = Scenario.Status.FAILED)
            throw e
        }
    }

    /**
     * Gets a scenario by ID.
     */
    fun getScenario(scenarioId: String): Scenario? = scenarios[scenarioId]

    /**
     * Persists scenarios to CAS.
     */
    fun persistScenarios(casDir: Path): Path {
        Files.createDirectories(casDir)
        val file = casDir.resolve("counterfactual-scenarios.json")
        val json = scenarios.values.map { s ->
            """
                {
                    "id": "${s.id}",
                    "name": "${s.name}",
                    "baseState": "${s.baseState}",
                    "intervention": "${s.intervention}",
                    "predictedOutcome": "${s.predictedOutcome ?: ""}",
                    "actualOutcome": "${s.actualOutcome ?: ""}",
                    "status": "${s.status}"
                }
            """.trimIndent()
        }.joinToString(",\n", "[\n", "\n]")
        Files.writeString(file, json)
        return file
    }

    /**
     * CLI command to show scenarios.
     */
    fun showScenarios(): String {
        return scenarios.values.joinToString("\n") { "${it.id} [${it.status}]: ${it.name} - ${it.intervention}" }
    }
}