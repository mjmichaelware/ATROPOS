/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.confidence

/**
 * SD7-T-005: Pareto decision envelope for single-bit human clarification.
 * Mathematical Invariant: Φ(t+1) < Φ(t) monotonic descent
 * Open-ended questions are rejected; single-bit envelope only.
 */
data class TradeOffVector(
    val latencyMsP99: Long,
    val memoryMbCeiling: Int,
    val consistencyModel: String,
    val costEstCents: Double,
    val complexityLocScore: Int
)

data class ParetoDecisionEnvelope(
    val intentEntropy: Double,
    val tradeOffOptions: List<TradeOffVector>,
    val selectedOptionIndex: Int?,
    val singleBitReceipt: String?
)
