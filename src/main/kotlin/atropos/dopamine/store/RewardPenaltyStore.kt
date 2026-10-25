package atropos.dopamine.store

import java.math.BigDecimal
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Deterministic neural reward and penalty store.
 * Stores all weights as exact decimal strings.
 */
class RewardPenaltyStore {
    data class RewardSignal(
        val atomId: String,
        val signalValue: String,
        val occurrenceCount: Int,
        val timestamp: Instant = Instant.now(),
        val diagnosticHash: String?,
        val causalParentSha: String?
    )

    private val signalHistory = ConcurrentHashMap<String, MutableList<RewardSignal>>()
    private val errorRecurrenceTracker = ConcurrentHashMap<String, AtomicInteger>()

    fun recordSignal(signal: RewardSignal) {
        signalHistory.computeIfAbsent(signal.atomId) { mutableListOf() }.add(signal)
        signal.diagnosticHash?.let { hash ->
            errorRecurrenceTracker.computeIfAbsent(hash) { AtomicInteger(0) }.incrementAndGet()
        }
    }

    fun getEscalatedPenalty(basePenalty: BigDecimal, diagnosticHash: String): BigDecimal {
        val count = errorRecurrenceTracker[diagnosticHash]?.get() ?: 1
        var multiplier = BigDecimal.ONE
        val factor = BigDecimal("1.2")
        for (i in 1 until count) {
            multiplier = multiplier.multiply(factor)
        }
        return basePenalty.multiply(multiplier)
    }

    fun queryByAtom(atomId: String): List<RewardSignal> = signalHistory[atomId] ?: emptyList()
}
