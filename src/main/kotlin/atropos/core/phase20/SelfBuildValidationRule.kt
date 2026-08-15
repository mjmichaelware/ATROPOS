/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.phase20

/**
 * Encapsulates conditions that must be met during the self-build loop before an amendment is persisted.
 */
class SelfBuildValidationRule(private val laws: Phase20Laws) {

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
}
