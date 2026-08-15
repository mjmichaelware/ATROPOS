/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.verification

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PrecedenceLatticeTest {

    private val lattice = PrecedenceLattice()

    @Test
    fun `checkPrecedence passes when role and token level are sufficient`() {
        // READ requires level 1
        assertTrue(lattice.checkPrecedence("GUEST", 1, "READ"))
        
        // APPROVE requires level 7 (DIRECTOR)
        assertTrue(lattice.checkPrecedence("DIRECTOR", 7, "APPROVE"))
        assertTrue(lattice.checkPrecedence("ROOT", 10, "APPROVE"))
    }

    @Test
    fun `checkPrecedence fails when token level is sufficient but role is insufficient`() {
        // Token has level 10, but role is only GUEST (level 1)
        // VERIFY requires level 6
        assertFalse(lattice.checkPrecedence("GUEST", 10, "VERIFY"))
    }

    @Test
    fun `checkPrecedence fails when role is sufficient but token level is insufficient`() {
        // Role is SYSTEM (level 9), but token is only level 5
        // RESTART requires level 9
        assertFalse(lattice.checkPrecedence("SYSTEM", 5, "RESTART"))
    }

    @Test
    fun `checkPrecedence handles unknown roles or actions defensively`() {
        // Unknown actions require level 10
        assertFalse(lattice.checkPrecedence("ADMIN", 8, "UNKNOWN_ACTION"))
        assertTrue(lattice.checkPrecedence("ROOT", 10, "UNKNOWN_ACTION"))
        
        // Unknown roles are assigned level 0
        assertFalse(lattice.checkPrecedence("INVALID_ROLE", 10, "READ"))
    }
}
