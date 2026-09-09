/* SPDX-License-Identifier: AGPL-3.0-only */
/**
 * B-SUP-022: Hypothesis Registry Pre-Registration
 *
 * Implements pre-registration of hypotheses to prevent
 * HARKing (Hypothesizing After Results Known).
 */
package atropos.core.supremacy

import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant

object HypothesisRegistry {
    data class Hypothesis(
        val id: String = java.util.UUID.randomUUID().toString(),
        val description: String,
        val prediction: String,
        val successCriteria: String,
        val registeredBy: String,
        val registeredAt: Instant = Instant.now(),
        val status: Status = Status.REGISTERED
    ) {
        enum class Status { REGISTERED, TESTING, CONFIRMED, REJECTED, WITHDRAWN }
    }

    data class TestResult(
        val hypothesisId: String,
        val outcome: Outcome,
        val evidence: String,
        val testedAt: Instant = Instant.now()
    ) {
        enum class Outcome { SUPPORTED, REJECTED, INCONCLUSIVE }
    }

    private val hypotheses = mutableMapOf<String, Hypothesis>()
    private val testResults = mutableListOf<TestResult>()

    /**
     * Registers a new hypothesis.
     */
    fun register(
        description: String,
        prediction: String,
        successCriteria: String,
        registeredBy: String
    ): Hypothesis {
        val hypothesis = Hypothesis(
            description = description,
            prediction = prediction,
            successCriteria = successCriteria,
            registeredBy = registeredBy
        )
        hypotheses[hypothesis.id] = hypothesis
        return hypothesis
    }

    /**
     * Records a test result for a hypothesis.
     */
    fun test(hypothesisId: String, outcome: TestResult.Outcome, evidence: String): Boolean {
        val hypothesis = hypotheses[hypothesisId] ?: return false
        if (hypothesis.status != Hypothesis.Status.REGISTERED && hypothesis.status != Hypothesis.Status.TESTING) {
            return false
        }
        hypotheses[hypothesisId] = hypothesis.copy(
            status = when (outcome) {
                TestResult.Outcome.SUPPORTED -> Hypothesis.Status.CONFIRMED
                TestResult.Outcome.REJECTED -> Hypothesis.Status.REJECTED
                else -> Hypothesis.Status.INCONCLUSIVE
            }
        )
        testResults.add(TestResult(hypothesisId, outcome, "evidence"))
        return true
    }

    /**
     * Gets a hypothesis by ID.
     */
    fun get(hypothesisId: String): Hypothesis? = hypotheses[hypothesisId]

    /**
     * Gets all hypotheses by status.
     */
    fun byStatus(status: Hypothesis.Status): List<Hypothesis> {
        return hypotheses.values.filter { it.status == status }.toList()
    }

    /**
     * Persists hypotheses to CAS.
     */
    fun persistRegistry(casDir: Path): Path {
        Files.createDirectories(casDir)
        val file = casDir.resolve("hypothesis-registry.json")
        val json = hypotheses.values.map { h ->
            """
                {
                    "id": "${h.id}",
                    "description": "${h.description}",
                    "prediction": "${h.prediction}",
                    "successCriteria": "${h.successCriteria}",
                    "registeredBy": "${h.registeredBy}",
                    "registeredAt": "${h.registeredAt}",
                    "status": "${h.status}"
                }
            """.trimIndent()
        }.joinToString(",\n", "[\n", "\n]")
        Files.writeString(file, json)
        return file
    }

    /**
     * CLI command to show registry.
     */
    fun showRegistry(): String {
        return hypotheses.values.joinToString("\n") { "${it.id} [${it.status}]: ${it.prediction}" }
    }
}