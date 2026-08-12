/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.storage

import java.time.Duration
import java.time.Instant

/**
 * Which tier each stored class sits in, and what moves it between tiers.
 *
 * `SUP.STOR.RETENTION-TIERS`: "Storage behavior is fully declarative and
 * testable; no hidden accumulation. Competitors have ad-hoc deletion."
 *
 * The declarative part is the point. Every collector reads this and none of
 * them decides anything — a collector that carried its own retention rule
 * would be a second policy, and the second policy is always the one nobody
 * remembers to update.
 *
 * Demotion is driven by the three signals the atom names: time, reference
 * count, and free-space pressure. They are not equivalent, and the order they
 * are tested in is the policy:
 *
 * 1. **Referenced** beats everything. A worktree an open run is using, or an
 *    evidence bundle a gate still needs, is `HOT` no matter how old it is or
 *    how full the disk is. Pressure is not a reason to destroy the record of
 *    the thing currently executing.
 * 2. **Age** demotes through `WARM` to `COLD` on the class's own windows.
 * 3. **Pressure** accelerates the walk but never skips step 1.
 */
class RetentionPolicy(private val classes: Map<String, RetentionRule> = DEFAULT_RULES) {

    fun ruleFor(storageClass: String): RetentionRule =
        classes[storageClass] ?: RetentionRule.CONSERVATIVE

    /** Every declared class, for `/storage policy`. */
    fun declared(): List<Pair<String, RetentionRule>> = classes.toList().sortedBy { it.first }

    /**
     * The tier an item belongs to right now.
     *
     * @param referenced whether anything still needs this item. Supplied by
     *   the caller rather than looked up here, because only the owning
     *   subsystem knows what an open reference means for its own data.
     * @param pressure how close storage is to its ceiling, 0.0 to 1.0.
     */
    fun tierFor(
        storageClass: String,
        age: Duration,
        referenced: Boolean,
        pressure: Double
    ): RetentionTier {
        if (referenced) return RetentionTier.HOT

        val rule = ruleFor(storageClass)
        if (!rule.reclaimable) return RetentionTier.HOT

        val squeeze = if (pressure >= EMERGENCY_PRESSURE) EMERGENCY_ACCELERATION else 1.0
        val warmWindow = rule.warmFor.dividedBy(scale(squeeze))
        val coldWindow = rule.coldFor.dividedBy(scale(squeeze))

        return when {
            age < warmWindow -> RetentionTier.WARM
            age < coldWindow -> RetentionTier.COLD
            else -> RetentionTier.DELETE
        }
    }

    /** Duration division takes a long; keep the conversion in one place. */
    private fun scale(factor: Double): Long = factor.toLong().coerceAtLeast(1)

    companion object {
        /** Above this fraction of the ceiling, retention windows are halved. */
        const val EMERGENCY_PRESSURE: Double = 0.95
        const val EMERGENCY_ACCELERATION: Double = 2.0

        /**
         * The classes ATROPOS actually writes, mapped to windows.
         *
         * Secrets and the configuration are declared non-reclaimable rather
         * than given a long window. A window is a statement about when
         * deletion becomes acceptable, and for these it never does — deleting
         * the vault to free space would trade a full disk for an unrecoverable
         * one.
         */
        val DEFAULT_RULES: Map<String, RetentionRule> = mapOf(
            "worktrees" to RetentionRule(Duration.ofDays(2), Duration.ofDays(7)),
            "evidence" to RetentionRule(Duration.ofDays(7), Duration.ofDays(30)),
            "runs" to RetentionRule(Duration.ofDays(7), Duration.ofDays(30)),
            "artifacts" to RetentionRule(Duration.ofDays(3), Duration.ofDays(14)),
            "backups" to RetentionRule(Duration.ofDays(7), Duration.ofDays(21)),
            "context-cache" to RetentionRule(Duration.ofDays(1), Duration.ofDays(3)),
            "cas" to RetentionRule(Duration.ofDays(14), Duration.ofDays(60)),
            "uploads" to RetentionRule(Duration.ofDays(3), Duration.ofDays(14)),
            "secrets" to RetentionRule.NEVER,
            "config" to RetentionRule.NEVER,
            "territory" to RetentionRule.NEVER
        )
    }
}

/**
 * @param warmFor how long after last use an item stays inspectable.
 * @param coldFor how long after that its content may be dropped, keeping only
 *   the hash. Past this it is eligible for deletion.
 */
data class RetentionRule(
    val warmFor: Duration,
    val coldFor: Duration,
    val reclaimable: Boolean = true
) {
    init {
        require(!reclaimable || coldFor >= warmFor) {
            "coldFor must not precede warmFor"
        }
    }

    fun describe(): String =
        if (!reclaimable) "never reclaimed"
        else "warm ${warmFor.toDays()}d, cold ${coldFor.toDays()}d, then deletable"

    companion object {
        val NEVER = RetentionRule(Duration.ZERO, Duration.ZERO, reclaimable = false)

        /** What an undeclared class gets: kept long, never surprising. */
        val CONSERVATIVE = RetentionRule(Duration.ofDays(30), Duration.ofDays(90))
    }
}

/** Age of something last touched at [lastUsed], as of [now]. */
fun ageOf(lastUsed: Instant, now: Instant = Instant.now()): Duration =
    Duration.between(lastUsed, now).let { if (it.isNegative) Duration.ZERO else it }
