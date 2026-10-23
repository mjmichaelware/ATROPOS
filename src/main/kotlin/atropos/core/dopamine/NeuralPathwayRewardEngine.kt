/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.dopamine

import java.security.MessageDigest
import kotlin.math.pow

/**
 * Deterministic Reward Escalation & Neural-Pathway Value Amplification Engine.
 *
 * Implements Section 2 of the ATROPOS Telemetry & Reward Realignment Specification:
 * - Deterministic and hash-addressable signal weighting.
 * - Base signals: Approved (+1.0), Rejected (-1.0), Vacuous Assertion (-1.5), Surviving Mutant (-1.5), Fraud (-10.0).
 * - Repeated exact error escalation: penalty * (1.2 ^ occurrenceCount).
 * - Related-error value amplification: +0.15 value multiplier for learning priority.
 * - Definitive fix bonus: +2.0 bonus for permanent resolution.
 */
object NeuralPathwayRewardEngine {

    const val BASE_APPROVED_HUNK: Double = 1.0
    const val BASE_REJECTED_HUNK: Double = -1.0
    const val BASE_VACUOUS_ASSERTION: Double = -1.5
    const val BASE_SURVIVING_MUTANT: Double = -1.5
    const val HARD_FRAUD_PENALTY: Double = -10.0
    const val DEFINITIVE_FIX_BONUS: Double = 2.0

    const val REPEATED_ERROR_ESCALATION_FACTOR: Double = 1.2
    const val RELATED_ERROR_AMPLIFICATION_MULTIPLIER: Double = 0.15

    enum class SignalCategory {
        APPROVED_HUNK,
        REJECTED_HUNK,
        VACUOUS_ASSERTION,
        SURVIVING_MUTANT,
        FRAUD_ATTEMPT,
        DEFINITIVE_FIX
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
     * Computes the deterministic reward/penalty score for an event given its historical occurrences.
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
            SignalCategory.DEFINITIVE_FIX -> DEFINITIVE_FIX_BONUS
        }

        val occurrences = priorOccurrenceCount.coerceAtLeast(0)
        var finalValue = base

        // Penalties escalate when the exact error repeats: penalty * (1.2 ^ count)
        if (base < 0.0 && occurrences > 0 && category != SignalCategory.FRAUD_ATTEMPT) {
            finalValue = base * REPEATED_ERROR_ESCALATION_FACTOR.pow(occurrences.toDouble())
        }

        // Related errors amplify learning priority by +0.15
        if (sharesClusterWithPriorError && base < 0.0) {
            val amplification = finalValue * RELATED_ERROR_AMPLIFICATION_MULTIPLIER
            finalValue += amplification // makes penalty slightly more severe/prominent
        }

        val hash = if (diagnosticFingerprint.isNotBlank()) {
            sha256Hex(diagnosticFingerprint)
        } else {
            sha256Hex("${category.name}-$occurrences-$sharesClusterWithPriorError")
        }

        val rationale = buildString {
            append("${category.name}: base=$base")
            if (occurrences > 0) append(", repeatedCount=$occurrences, factor=${REPEATED_ERROR_ESCALATION_FACTOR.pow(occurrences.toDouble())}")
            if (sharesClusterWithPriorError) append(", relatedClusterAmplified=+$RELATED_ERROR_AMPLIFICATION_MULTIPLIER")
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

    /**
     * Determines whether an error qualifies as high-value negative under backpressure.
     */
    fun isHighValueNegative(category: SignalCategory, score: Double): Boolean {
        return category == SignalCategory.FRAUD_ATTEMPT ||
                category == SignalCategory.SURVIVING_MUTANT ||
                category == SignalCategory.VACUOUS_ASSERTION ||
                score <= -1.0
    }

    private fun sha256Hex(input: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val bytes = md.digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
