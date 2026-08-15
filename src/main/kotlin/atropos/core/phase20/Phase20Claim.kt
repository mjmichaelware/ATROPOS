/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.phase20

/**
 * Defines typed L0-L6 claims for the Phase 20 self-improvement loop.
 * Each claim requires specific evidentiary boundaries before acceptance.
 */
enum class ClaimLevel {
    L0_DRAFT,
    L1_SYNTAX_VALID,
    L2_TESTS_PASS,
    L3_COVERAGE_MET,
    L4_INVARIANT_SAFE,
    L5_METRIC_IMPROVED,
    L6_VERIFIED
}

data class Phase20Claim(
    val claimId: String,
    val proposalId: String,
    val level: ClaimLevel,
    val evidenceHash: String
) {
    init {
        require(claimId.isNotBlank()) { "Claim ID cannot be blank" }
        require(proposalId.isNotBlank()) { "Proposal ID cannot be blank" }
        require(evidenceHash.isNotBlank()) { "Evidence hash cannot be blank" }
    }

    fun isPromotableTo(targetLevel: ClaimLevel): Boolean {
        return targetLevel.ordinal == level.ordinal + 1
    }
}
