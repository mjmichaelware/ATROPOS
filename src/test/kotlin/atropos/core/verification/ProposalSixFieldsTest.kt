/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.verification

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import java.time.Instant

class ProposalSixFieldsTest {

    @Test
    fun `enforces presence of all six proposal fields`() {
        val valid = Proposal(
            "p1", "base", "target", listOf("g1"), listOf("/tmp"), mapOf("risk" to 0.1), "rollback_script"
        )
        assertTrue(ProposalSixFields.validate(valid))

        val invalid = Proposal(
            "p2", "base", "target", listOf("g1"), null, mapOf("risk" to 0.1), "rollback_script"
        )
        assertFalse(ProposalSixFields.validate(invalid))
    }

    @Test
    fun `legacy proposal converts into canonical phase twenty shape`() {
        val legacy = Proposal(
            "p2", "base", "target", listOf("g1"), listOf("src"), mapOf("risk" to 0.1), "rollback"
        )
        val canonical = ProposalSixFields.toCanonical(legacy, "legacy-import", Instant.EPOCH)
        assertEquals("p2", canonical?.id)
        assertEquals("legacy-import", canonical?.proposedBy)
        assertTrue(canonical?.isComplete() == true)
    }
}
