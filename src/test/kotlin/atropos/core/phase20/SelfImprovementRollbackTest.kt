/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.phase20

import kotlin.test.*

class SelfImprovementRollbackTest {
    @Test
    fun testDeployAndRollback() {
        val rollbackSystem = SelfImprovementRollback(mutableListOf())
        
        rollbackSystem.deployAmendment("amd-001")
        assertTrue(rollbackSystem.getActiveAmendments().contains("amd-001"))
        
        val rolledBack = rollbackSystem.triggerRollback("amd-001", "Violation of memory limits")
        assertTrue(rolledBack)
        assertFalse(rollbackSystem.getActiveAmendments().contains("amd-001"))
        
        assertFailsWith<IllegalArgumentException> {
            rollbackSystem.triggerRollback("amd-002", "")
        }
    }
}
