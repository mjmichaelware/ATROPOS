package atropos.dopamine.neural

import java.math.BigDecimal

/**
 * Human-neural-network pathway strengthening and pruning analogue.
 * Implements exact compounding escalation, related-error value amplification,
 * definitive fix bonus, backward DAG credit assignment, and habituation.
 */
object NeuralPathwayRewardEngine {
    val BASE_APPROVED = BigDecimal("1.0")
    val BASE_REJECTED = BigDecimal("-1.0")
    val BASE_VACUOUS = BigDecimal("-1.5")
    val HARD_FRAUD = BigDecimal("-10.0")
    val DEFINITIVE_FIX_BONUS = BigDecimal("2.0")
    val ESCALATION_FACTOR = BigDecimal("1.2")
    val RELATED_ERROR_AMPLIFICATION = BigDecimal("0.15")
    val HABITUATION_FACTOR = BigDecimal("0.9")

    fun amplifyRelatedErrors(signals: List<BigDecimal>): List<BigDecimal> {
        return signals.map { it.add(RELATED_ERROR_AMPLIFICATION) }
    }

    fun applyHabituation(baseReward: BigDecimal, repetitionCount: Int): BigDecimal {
        var current = baseReward
        repeat(repetitionCount) {
            current = current.multiply(HABITUATION_FACTOR)
        }
        return current
    }
}
