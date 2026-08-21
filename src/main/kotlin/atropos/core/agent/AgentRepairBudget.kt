package atropos.core.agent

import java.time.Duration
import java.time.Instant

/** Bounds diagnostic repair without turning a failed change into an endless loop. */
data class AgentRepairBudget(
    val maxAttempts: Int = 3,
    val maxDuration: Duration = Duration.ofMinutes(30)
) {
    init {
        require(maxAttempts > 0) { "repair attempt budget must be positive" }
        require(!maxDuration.isNegative && !maxDuration.isZero) { "repair duration budget must be positive" }
    }

    fun allows(attemptsUsed: Int, startedAt: Instant, now: Instant): Boolean =
        attemptsUsed < maxAttempts && Duration.between(startedAt, now) <= maxDuration

    fun exhaustedReason(attemptsUsed: Int, startedAt: Instant, now: Instant): String? {
        if (attemptsUsed >= maxAttempts) return "repair attempt budget exhausted ($maxAttempts)"
        if (Duration.between(startedAt, now) > maxDuration) {
            return "repair duration budget exhausted (${maxDuration.toMinutes()} minutes)"
        }
        return null
    }
}
