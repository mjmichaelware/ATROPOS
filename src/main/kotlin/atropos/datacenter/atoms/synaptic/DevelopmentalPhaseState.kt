/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.datacenter.atoms.synaptic

/**
 * DevelopmentalPhaseState (DC-TEL-202)
 *
 * Tracks the developmental phase transitions of the ATROPOS autonomous organism:
 * Sensorimotor Exploratory -> Synaptic Pruning -> Formal Operations -> Critical Period Gated.
 */
enum class DevelopmentalPhase {
    SENSORIMOTOR_EXPLORATORY,
    SYNAPTIC_PRUNING,
    FORMAL_OPERATIONS,
    CRITICAL_PERIOD_GATED
}

data class DevelopmentalPhaseState(
    val phaseId: String,
    val currentPhase: DevelopmentalPhase,
    val phaseProgress: Double,
    val plasticityGain: Double,
    val pruningThreshold: Double,
    val criticalWindowOpen: Boolean,
    val cumulativeExperienceTokens: Long
)
