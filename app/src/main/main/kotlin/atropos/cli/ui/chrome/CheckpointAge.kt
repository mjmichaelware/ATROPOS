/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.ui.chrome

import java.time.Clock
import java.time.Duration
import java.time.Instant

/**
 * How long ago the last checkpoint was written, as the sticky header states it.
 *
 * The header's job here is trust: an operator glancing at `4m` is being told the
 * system's durable record of its own work is four minutes old. That claim is only
 * worth making when it is true, so this type follows `HomeStateProvider`'s rule —
 * a checkpoint whose timestamp cannot be read renders as [Unknown], never as
 * `0s` and never as "fresh". Collapsing "I do not know when work was last saved"
 * into "work was just saved" is the single most dangerous lie this header could
 * tell.
 *
 * A timestamp in the future is a third, separate state. It is not unknown — we
 * read it fine — and it is not an age, because ages are not negative. It means
 * the record and the clock disagree, which is a fault worth showing rather than
 * clamping to zero.
 *
 * The clock is injected at the [of] boundary; [label] is pure formatting and can
 * never reach for the current time.
 */
sealed interface CheckpointAge {

    /** A real elapsed duration since the checkpoint was written. */
    data class Known(val age: Duration) : CheckpointAge {
        init {
            require(!age.isNegative) { "checkpoint age cannot be negative: $age" }
        }
    }

    /** No checkpoint timestamp could be read. Absence of knowledge, not absence of age. */
    object Unknown : CheckpointAge

    /** The checkpoint timestamp is ahead of the clock; the two sources disagree. */
    object Skewed : CheckpointAge

    /**
     * Short human string for the header: `12s`, `4m`, `2h`, `3d`.
     *
     * Deliberately coarse. The header is glanced at, not read, so a single
     * magnitude carries the signal and a second unit would only cost columns.
     */
    fun label(): String = when (this) {
        is Known -> shortForm(age)
        Unknown -> UNKNOWN_LABEL
        Skewed -> SKEWED_LABEL
    }

    companion object {
        const val UNKNOWN_LABEL = "unknown"
        const val SKEWED_LABEL = "clock skew"

        private const val SECONDS_PER_MINUTE = 60L
        private const val SECONDS_PER_HOUR = 3_600L
        private const val SECONDS_PER_DAY = 86_400L

        /** Coarse single-magnitude form. Exposed so callers can width-budget the label. */
        fun shortForm(age: Duration): String {
            val seconds = age.seconds
            return when {
                seconds < SECONDS_PER_MINUTE -> "${seconds}s"
                seconds < SECONDS_PER_HOUR -> "${age.toMinutes()}m"
                seconds < SECONDS_PER_DAY -> "${age.toHours()}h"
                else -> "${age.toDays()}d"
            }
        }

        /**
         * Reads an age from a checkpoint timestamp and an injected clock.
         *
         * `null` is the honest input for "the runtime has no checkpoint timestamp
         * to give"; callers must pass it rather than substituting the current
         * instant, which would manufacture a checkpoint that never happened.
         */
        fun of(checkpointAt: Instant?, clock: Clock): CheckpointAge {
            if (checkpointAt == null) return Unknown
            val now = clock.instant()
            if (checkpointAt.isAfter(now)) return Skewed
            return Known(Duration.between(checkpointAt, now))
        }

        /**
         * Reads an age from an epoch-millisecond field, as durable records store it.
         *
         * A non-positive epoch is treated as absent: stores in this repo write `0`
         * for "never set", and a header claiming the last checkpoint was in 1970
         * is worse than one admitting it does not know.
         */
        fun ofEpochMillis(epochMillis: Long?, clock: Clock): CheckpointAge =
            if (epochMillis == null || epochMillis <= 0L) {
                Unknown
            } else {
                of(Instant.ofEpochMilli(epochMillis), clock)
            }
    }
}
