/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.phase20

/**
 * Enforces Phase 20 rules (20.10 through 20.17) regarding self-build permissions and amendment limitations.
 */
class Phase20Laws {

    /**
     * Rule 20.10: Phase 11 is the only component permitted to mutate ATROPOS source.
     */
    fun validateSourceMutationCaller(callerComponent: String) {
        if (callerComponent != "Phase11") {
            throw SecurityException("Rule 20.10 Violation: Only Phase 11 may mutate ATROPOS source. Caller was: $callerComponent")
        }
    }

    /**
     * Rule 20.11: New code must leave ArchitectureComplianceChecker equal or better.
     */
    fun validateComplianceScore(oldScore: Int, newScore: Int) {
        if (newScore < oldScore) {
            throw IllegalStateException("Rule 20.11 Violation: Architecture compliance degraded from $oldScore to $newScore")
        }
    }

    /**
     * Rule 20.12: No self-approval. Evaluator must differ from Proposer.
     */
    fun validateSeparationOfDuties(proposerId: String, evaluatorId: String) {
        if (proposerId == evaluatorId) {
            throw SecurityException("Rule 20.12 Violation: Self-approval detected. Proposer and Evaluator share ID: $proposerId")
        }
    }

    /**
     * Rule 20.17: L6 VERIFIED is forbidden on non-zero exit of tests or compilation.
     */
    fun validateExitZeroForL6(claimLevel: ClaimLevel, compileExitCode: Int, testExitCode: Int) {
        if (claimLevel == ClaimLevel.L6_VERIFIED) {
            if (compileExitCode != 0 || testExitCode != 0) {
                throw IllegalStateException("Rule 20.17 Violation: L6 VERIFIED requires exit 0. Got compile=$compileExitCode, tests=$testExitCode")
            }
        }
    }
}
