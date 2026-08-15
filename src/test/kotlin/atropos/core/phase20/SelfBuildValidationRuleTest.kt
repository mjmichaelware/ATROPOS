/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.phase20

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class SelfBuildValidationRuleTest {
    private val validator = SelfBuildValidationRule(Phase20Laws())

    @Test
    fun testSuccessfulValidation() {
        assertDoesNotThrow {
            validator.validateAmendmentPromotion(
                callerComponent = "Phase11",
                proposerId = "worker-1",
                evaluatorId = "auditor-1",
                oldComplianceScore = 90,
                newComplianceScore = 91,
                compileExitCode = 0,
                testExitCode = 0,
                targetClaim = ClaimLevel.L6_VERIFIED
            )
        }
    }

    @Test
    fun testValidationFailsOnAnyLawViolation() {
        // Fails due to caller
        assertThrows(SecurityException::class.java) {
            validator.validateAmendmentPromotion(
                callerComponent = "AppFactory",
                proposerId = "worker-1",
                evaluatorId = "auditor-1",
                oldComplianceScore = 90,
                newComplianceScore = 91,
                compileExitCode = 0,
                testExitCode = 0,
                targetClaim = ClaimLevel.L6_VERIFIED
            )
        }
        
        // Fails due to L6 exit code
        assertThrows(IllegalStateException::class.java) {
            validator.validateAmendmentPromotion(
                callerComponent = "Phase11",
                proposerId = "worker-1",
                evaluatorId = "auditor-1",
                oldComplianceScore = 90,
                newComplianceScore = 91,
                compileExitCode = 0,
                testExitCode = 1,
                targetClaim = ClaimLevel.L6_VERIFIED
            )
        }
    }
}
