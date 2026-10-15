/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.verification

import kotlin.math.min

data class DiscreteCompletionVector(
    val cImpl: Int,
    val cInt: Int,
    val cVer: Int,
    val cEv: Int
) {
    init {
        require(cImpl in 0..1 && cInt in 0..1 && cVer in 0..1 && cEv in 0..1) {
            "Completion scalars must be strictly discrete binary {0, 1}"
        }
    }

    val cReal: Int
        get() = min(min(cImpl, cInt), min(cVer, cEv))
}

object CompletionCalculus {
    fun compute(cImpl: Int, cInt: Int, cVer: Int, cEv: Int): DiscreteCompletionVector {
        return DiscreteCompletionVector(cImpl, cInt, cVer, cEv)
    }

    fun isComplete(vector: DiscreteCompletionVector): Boolean = vector.cReal == 1
}