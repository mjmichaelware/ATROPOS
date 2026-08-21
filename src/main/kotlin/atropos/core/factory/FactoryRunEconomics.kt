/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.factory

data class FactoryRunEconomics(
    val wallTimeMillis: Long,
    val providerCalls: Int?,
    val tokens: Long?,
    val atomsDone: Int,
    val atomsFailed: Int,
    val atomsBlocked: Int,
    val softSkips: Int,
    val gateDecision: String,
    val terminationReason: String
) {
    fun render(): String = listOf(
        "wall_time_ms=$wallTimeMillis",
        "provider_calls=${providerCalls ?: "unknown"}",
        "tokens=${tokens ?: "unknown"}",
        "atoms_done=$atomsDone",
        "atoms_failed=$atomsFailed",
        "atoms_blocked=$atomsBlocked",
        "soft_skips=$softSkips",
        "gate_decision=$gateDecision",
        "termination_reason=$terminationReason"
    ).joinToString(" ")
}
