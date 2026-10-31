/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.dopamine.signals

/**
 * PediatricHabituationVector (DC-DOP-022)
 *
 * Implements exponential habituation decay on repeated identical actions.
 * Attenuates dopamine release for repetitive actions to discourage looping.
 */
data class PediatricHabituationVector(
    val actionSignature: String,
    val totalStimulusCount: Int,
    val decayConstantEta: Double,
    val effectiveMultiplier: Double,
    val saturationCeilingExceeded: Boolean
)
