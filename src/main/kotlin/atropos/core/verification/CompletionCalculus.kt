/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.verification

import kotlin.math.min

data class ComponentCompletion(
    val implementationPercent: Double,
    val integrationPercent: Double,
    val verificationPercent: Double,
    val evidencePercent: Double
)

object CompletionCalculus {
    fun calculateRealCompletion(comp: ComponentCompletion): Double {
        return min(
            min(comp.implementationPercent, comp.integrationPercent),
            min(comp.verificationPercent, comp.evidencePercent)
        )
    }
}
