/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.phase20

import kotlin.test.*
import atropos.testing.assertDoesNotThrow

class Phase20LawsTest {
    private val laws = Phase20Laws()

    @Test
    fun testRule2010_MutationCaller() {
        assertDoesNotThrow { laws.validateSourceMutationCaller("Phase11") }
        
        val e = assertFailsWith<SecurityException> {
            laws.validateSourceMutationCaller("Phase20_SelfImprovementLoop")
        }
        assertTrue(e.message!!.contains("Only Phase 11 may mutate"))
    }

    @Test
    fun testRule2011_ComplianceScore() {
        assertDoesNotThrow { laws.validateComplianceScore(90, 95) }
        assertDoesNotThrow { laws.validateComplianceScore(95, 95) }
        
        val e = assertFailsWith<IllegalStateException> {
            laws.validateComplianceScore(95, 90)
        }
        assertTrue(e.message!!.contains("Architecture compliance degraded"))
    }

    @Test
    fun testRule2012_SeparationOfDuties() {
        assertDoesNotThrow { laws.validateSeparationOfDuties("agent-1", "agent-2") }
        
        val e = assertFailsWith<SecurityException> {
            laws.validateSeparationOfDuties("agent-1", "agent-1")
        }
        assertTrue(e.message!!.contains("Self-approval detected"))
    }

    @Test
    fun testRule2017_ExitZeroForL6() {
        assertDoesNotThrow { laws.validateExitZeroForL6(ClaimLevel.L6_VERIFIED, 0, 0) }
        assertDoesNotThrow { laws.validateExitZeroForL6(ClaimLevel.L5_METRIC_IMPROVED, 1, 0) }
        
        val e = assertFailsWith<IllegalStateException> {
            laws.validateExitZeroForL6(ClaimLevel.L6_VERIFIED, 0, 1)
        }
        assertTrue(e.message!!.contains("L6 VERIFIED requires exit 0"))
    }
}
