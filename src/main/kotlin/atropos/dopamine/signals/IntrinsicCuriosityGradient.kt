/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.dopamine.signals

/**
 * IntrinsicCuriosityGradient (DC-DOP-023)
 *
 * Implements intrinsic curiosity rewards based on state entropy reduction
 * and Friston free energy minimization.
 */
data class IntrinsicCuriosityGradient(
    val gradientId: String,
    val stateEntropyBefore: Double,
    val stateEntropyAfter: Double,
    val expectedInformationGain: Double,
    val rewardGranted: Double,
    val timestamp: Long
)
