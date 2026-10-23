/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.dopamine

import java.security.MessageDigest
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Deterministic Neural-Pathway Value Amplification & Reward Engine.
 *
 * Implements deterministic neural-pathway mathematical algorithms:
 * 1. Deterministic base signal valuation.
 * 2. Exponential repeated-error penalty escalation: S(e, n) = S_base * (1.2^n).
 * 3. Tarjan Strongly Connected Components (SCC) cluster analysis for related-error value amplification (+0.15).
 * 4. Definitive fix bonus detection with negative debt compensation: max(2.0, debt * 0.6).
 * 5. Hash-addressable diagnostic & AST-edit fingerprinting.
 */
object NeuralPathwayRewardEngine {

    const val BASE_APPROVED_HUNK: Double = 1.0
    const val BASE_REJECTED_HUNK: Double = -1.0
    const val BASE_VACUOUS_ASSERTION: Double = -1.5
    const val BASE_SURVIVING_MUTANT: Double = -1.5
    const val HARD_FRAUD_PENALTY: Double = -10.0
    const val BASE_DEFINITIVE_FIX_BONUS: Double = 2.0

    const val REPEATED_ERROR_ESCALATION_FACTOR: Double = 1.2
    const val RELATED_ERROR_AMPLIFICATION_MULTIPLIER: Double = 0.15
    const val STABILITY_HORIZON_STEPS: Int = 3

    enum class SignalCategory {
        APPROVED_HUNK,
        REJECTED_HUNK,
        VACUOUS_ASSERTION,
        SURVIVING_MUTANT,
        FRAUD_ATTEMPT,
        DEFINITIVE_FIX
    }

    data class DiagnosticFingerprint(
        val filePath: String,
        val symbolScope: String,
        val errorCode: String,
        val astEditHash: String
    ) {
        val compositeHash: String by lazy {
            sha256Hex("$filePath:$symbolScope:$errorCode:$astEditHash")
        }
    }

    data class WeightedSignal(
        val category: SignalCategory,
        val baseScore: Double,
        val occurrenceCount: Int,
        val isRelatedToCluster: Boolean,
        val finalScore: Double,
        val diagnosticHash: String,
        val rationale: String
    )

    /**
     * Stateful Trajectory Error Tracker for deterministic neural learning math.
     */
    class TrajectoryTracker {
        private val occurrenceCounts = mutableMapOf<String, Int>()
        private val accumulatedPenalties = mutableMapOf<String, Double>()
        private val stepLastObserved = mutableMapOf<String, Int>()
        private val errorGraph = mutableMapOf<String, MutableSet<String>>()
        private var currentStep: Int = 0

        fun advanceStep(): Int {
            currentStep++
            return currentStep
        }

        fun recordOccurrence(fingerprint: DiagnosticFingerprint, basePenalty: Double): Int {
            val key = fingerprint.compositeHash
            val count = occurrenceCounts.getOrDefault(key, 0) + 1
            occurrenceCounts[key] = count
            accumulatedPenalties[key] = accumulatedPenalties.getOrDefault(key, 0.0) + basePenalty
            stepLastObserved[key] = currentStep
            return count - 1 // return prior occurrences
        }

        fun linkRelatedErrors(fingerprintA: DiagnosticFingerprint, fingerprintB: DiagnosticFingerprint) {
            val a = fingerprintA.compositeHash
            val b = fingerprintB.compositeHash
            errorGraph.getOrPut(a) { mutableSetOf() }.add(b)
            errorGraph.getOrPut(b) { mutableSetOf() }.add(a)
        }

        fun areInSameCluster(fingerprintA: DiagnosticFingerprint, fingerprintB: DiagnosticFingerprint): Boolean {
            val a = fingerprintA.compositeHash
            val b = fingerprintB.compositeHash
            if (a == b) return true
            if (fingerprintA.symbolScope.isNotBlank() && fingerprintA.symbolScope == fingerprintB.symbolScope) return true
            if (fingerprintA.filePath.isNotBlank() && fingerprintA.filePath == fingerprintB.filePath) return true

            // Run Tarjan SCC on the error graph to determine relational components
            val components = TarjanGraphClusterer.findStronglyConnectedComponents(errorGraph)
            return components.any { it.contains(a) && it.contains(b) }
        }

        fun evaluateDefinitiveFix(fingerprint: DiagnosticFingerprint): Double? {
            val key = fingerprint.compositeHash
            val priorCount = occurrenceCounts.getOrDefault(key, 0)
            if (priorCount < 1) return null

            val lastSeen = stepLastObserved.getOrDefault(key, 0)
            val elapsed = currentStep - lastSeen
            if (elapsed >= STABILITY_HORIZON_STEPS) {
                val pastDebt = kotlin.math.abs(accumulatedPenalties.getOrDefault(key, 0.0))
                return max(BASE_DEFINITIVE_FIX_BONUS, pastDebt * 0.6)
            }
            return null
        }
    }

    /**
     * Computes the deterministic reward/penalty score given historical state and cluster topology.
     */
    fun computeScore(
        category: SignalCategory,
        priorOccurrenceCount: Int = 0,
        sharesClusterWithPriorError: Boolean = false,
        diagnosticFingerprint: String = ""
    ): WeightedSignal {
        val base = when (category) {
            SignalCategory.APPROVED_HUNK -> BASE_APPROVED_HUNK
            SignalCategory.REJECTED_HUNK -> BASE_REJECTED_HUNK
            SignalCategory.VACUOUS_ASSERTION -> BASE_VACUOUS_ASSERTION
            SignalCategory.SURVIVING_MUTANT -> BASE_SURVIVING_MUTANT
            SignalCategory.FRAUD_ATTEMPT -> HARD_FRAUD_PENALTY
            SignalCategory.DEFINITIVE_FIX -> BASE_DEFINITIVE_FIX_BONUS
        }

        val occurrences = max(0, priorOccurrenceCount)
        var finalValue = base

        // Repeated exact error escalation: base * (1.2 ^ n)
        if (base < 0.0 && occurrences > 0 && category != SignalCategory.FRAUD_ATTEMPT) {
            finalValue = base * REPEATED_ERROR_ESCALATION_FACTOR.pow(occurrences.toDouble())
        }

        // Related error cluster value amplification (+0.15)
        if (sharesClusterWithPriorError && base < 0.0) {
            val amplification = finalValue * RELATED_ERROR_AMPLIFICATION_MULTIPLIER
            finalValue += amplification
        }

        val hash = if (diagnosticFingerprint.isNotBlank()) {
            sha256Hex(diagnosticFingerprint)
        } else {
            sha256Hex("${category.name}-$occurrences-$sharesClusterWithPriorError")
        }

        val rationale = buildString {
            append("${category.name}: base=$base")
            if (occurrences > 0) append(", repeatedOccurrences=$occurrences, factor=${REPEATED_ERROR_ESCALATION_FACTOR.pow(occurrences.toDouble())}")
            if (sharesClusterWithPriorError) append(", relatedClusterAmplification=+$RELATED_ERROR_AMPLIFICATION_MULTIPLIER")
            append(", finalScore=$finalValue")
        }

        return WeightedSignal(
            category = category,
            baseScore = base,
            occurrenceCount = occurrences,
            isRelatedToCluster = sharesClusterWithPriorError,
            finalScore = finalValue,
            diagnosticHash = hash,
            rationale = rationale
        )
    }

    fun isHighValueNegative(category: SignalCategory, score: Double): Boolean {
        return category == SignalCategory.FRAUD_ATTEMPT ||
                category == SignalCategory.SURVIVING_MUTANT ||
                category == SignalCategory.VACUOUS_ASSERTION ||
                score <= -1.0
    }

    fun sha256Hex(input: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val bytes = md.digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}

/**
 * Functional Tarjan's Strongly Connected Components (SCC) algorithm for relational graph clustering.
 */
object TarjanGraphClusterer {
    fun findStronglyConnectedComponents(graph: Map<String, Set<String>>): List<Set<String>> {
        var index = 0
        val stack = ArrayDeque<String>()
        val onStack = mutableSetOf<String>()
        val indices = mutableMapOf<String, Int>()
        val lowLinks = mutableMapOf<String, Int>()
        val sccs = mutableListOf<Set<String>>()

        fun strongConnect(node: String) {
            indices[node] = index
            lowLinks[node] = index
            index++
            stack.addLast(node)
            onStack.add(node)

            val neighbors = graph[node] ?: emptySet()
            for (neighbor in neighbors) {
                if (!indices.containsKey(neighbor)) {
                    strongConnect(neighbor)
                    lowLinks[node] = min(lowLinks.getValue(node), lowLinks.getValue(neighbor))
                } else if (onStack.contains(neighbor)) {
                    lowLinks[node] = min(lowLinks.getValue(node), indices.getValue(neighbor))
                }
            }

            if (lowLinks[node] == indices[node]) {
                val component = mutableSetOf<String>()
                while (true) {
                    val w = stack.removeLast()
                    onStack.remove(w)
                    component.add(w)
                    if (w == node) break
                }
                sccs.add(component)
            }
        }

        for (node in graph.keys) {
            if (!indices.containsKey(node)) {
                strongConnect(node)
            }
        }

        return sccs
    }
}
