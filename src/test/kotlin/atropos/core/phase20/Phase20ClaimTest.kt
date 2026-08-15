/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.phase20

import kotlin.test.*

class Phase20ClaimTest {
    @Test
    fun testClaimCreationAndPromotion() {
        val claim = Phase20Claim("claim-1", "prop-1", ClaimLevel.L1_SYNTAX_VALID, "hash123")
        
        assertTrue(claim.isPromotableTo(ClaimLevel.L2_TESTS_PASS))
        assertFalse(claim.isPromotableTo(ClaimLevel.L3_COVERAGE_MET))
        assertFalse(claim.isPromotableTo(ClaimLevel.L0_DRAFT))
    }

    @Test
    fun testInvalidClaims() {
        assertFailsWith<IllegalArgumentException> {
            Phase20Claim("", "prop", ClaimLevel.L0_DRAFT, "hash")
        }
        assertFailsWith<IllegalArgumentException> {
            Phase20Claim("claim", "", ClaimLevel.L0_DRAFT, "hash")
        }
        assertFailsWith<IllegalArgumentException> {
            Phase20Claim("claim", "prop", ClaimLevel.L0_DRAFT, "")
        }
    }
}
