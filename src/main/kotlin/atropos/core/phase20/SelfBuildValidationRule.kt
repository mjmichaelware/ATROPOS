/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.phase20

/**
 * Encapsulates conditions that must be met during the self-build loop before an amendment is persisted.
 */
class SelfBuildValidationRule(
    private val laws: Phase20Laws,
    private val metaAdapter: Phase20StructuralMetaAdapter = Phase20StructuralMetaAdapter.SHARED
) {

    fun validateAmendmentPromotion(
        callerComponent: String,
        proposerId: String,
        evaluatorId: String,
        oldComplianceScore: Int,
        newComplianceScore: Int,
        compileExitCode: Int,
        testExitCode: Int,
        targetClaim: ClaimLevel
    ) {
        laws.validateSourceMutationCaller(callerComponent)
        laws.validateSeparationOfDuties(proposerId, evaluatorId)
        laws.validateComplianceScore(oldComplianceScore, newComplianceScore)
        laws.validateExitZeroForL6(targetClaim, compileExitCode, testExitCode)
    }

    /**
     * Enforces that candidate mutations produced during Phase 20 self-build adhere
     * to all permanent Long-Term Memory constraints escalated via the 2-strike rule.
     */
    fun validateCandidateAgainstPermanentConstraints(providerId: String, candidatePatch: String) {
        val violations = metaAdapter.validateCandidateAgainstPermanentConstraints(providerId, candidatePatch)
        if (violations.isNotEmpty()) {
            throw IllegalStateException(
                "Phase 20 self-build mutation rejected: violates permanent Long-Term Memory constraint(s): ${violations.joinToString("; ")}"
            )
        }
    }
}

