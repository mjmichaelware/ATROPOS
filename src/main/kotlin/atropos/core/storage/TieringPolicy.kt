/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.storage

import java.time.Duration

class TieringPolicy(
    private val warmAfter: Duration = Duration.ofHours(24),
    private val coldAfter: Duration = Duration.ofDays(7)
) {
    init { require(!warmAfter.isNegative && coldAfter >= warmAfter) }

    fun tierFor(age: Duration, pinned: Boolean, held: Boolean): RetentionTier = when {
        pinned || held || age < warmAfter -> RetentionTier.HOT
        age < coldAfter -> RetentionTier.WARM
        else -> RetentionTier.COLD
    }
}
