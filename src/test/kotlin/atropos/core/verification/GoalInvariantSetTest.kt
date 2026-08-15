/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.verification

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GoalInvariantSetTest {

    @Test
    fun `enforces target prohibitions successfully`() {
        val set = GoalInvariantSet(
            rootAuthorityHash = "root_hash_123",
            clauses = listOf(
                InvariantClause("C01", "hash_1", prohibition = true, targetPathPattern = "secure_keys")
            )
        )
        assertFalse(set.validateMutation("src/main/kotlin/secure_keys/Config.kt", isProhibitedAction = true))
        assertTrue(set.validateMutation("src/main/kotlin/normal/Config.kt", isProhibitedAction = true))
    }
}
