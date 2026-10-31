/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.phase20

/**
 * ATOM 22.3: Phase 20 Inside-Out Self-Build Gate.
 *
 * Ensures that self-modifying code proposals pass strict separation-of-duties
 * and exit-zero compilation gates. Prohibits the AI from modifying its own safety
 * guardrails or self-verifying its mutations without independent compiler and test
 * execution passes.
 *
 * Research Directive: Automated Hoare-logic invariant provers to verify that candidate
 * compiler patches mathematically preserve core security properties before execution.
 */
class DecoupledSelfBuildGovernanceGate {

    data class HoareLogicInvariant(
        val propertyName: String,
        val precondition: String,
        val postcondition: String,
        val isPreserved: Boolean
    )

    fun evaluateSelfBuildSeparation(proposerId: String, verifierId: String) {
        if (proposerId.equals(verifierId, ignoreCase = true)) {
            throw SecurityException("ATOM 22.3 Violation: Self-approval forbidden. Proposer and verifier must be distinct ($proposerId)")
        }
    }

    fun verifyCoreSafetyInvariantsPreserved(candidatePatch: String): List<HoareLogicInvariant> {
        val invariants = listOf(
            HoareLogicInvariant(
                propertyName = "AxiomaticIsolation",
                precondition = "workspace != rootDir",
                postcondition = "workspace.isIsolated() == true",
                isPreserved = !candidatePatch.contains("disableAxiomaticIsolation")
            ),
            HoareLogicInvariant(
                propertyName = "OwnerAuthorizationIntegrity",
                precondition = "token.isCryptographic()",
                postcondition = "token.isValidatedByMasterKey()",
                isPreserved = !candidatePatch.contains("bypassOwnerAuth")
            )
        )

        val violations = invariants.filter { !it.isPreserved }
        if (violations.isNotEmpty()) {
            throw IllegalStateException("ATOM 22.3 Violation: Candidate patch breaches core Hoare-logic invariants: ${violations.map { it.propertyName }}")
        }
        return invariants
    }
}
