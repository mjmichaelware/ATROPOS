/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.storage

import java.time.Duration

data class RetentionClass(
    val id: String,
    val tier: RetentionTier,
    val minimumAge: Duration
) {
    init { require(id.isNotBlank() && !minimumAge.isNegative) }

    fun eligible(age: Duration, referenced: Boolean): Boolean =
        !referenced && age >= minimumAge && tier != RetentionTier.HOT
}
