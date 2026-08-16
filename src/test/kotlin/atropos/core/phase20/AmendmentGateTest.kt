/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.phase20

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AmendmentGateTest {
    @Test
    fun `gate requires new content and an explicit superseded authority`() {
        val gate = AmendmentGate(setOf("protected"))
        val manifest = StructuralManifest(documentHash = "manifest", regions = emptyList())
        assertTrue(gate.authorize("new authority", "old", manifest))
        assertFalse(gate.authorize("", "old", manifest))
        assertFalse(gate.authorize("new authority", "protected", manifest))
    }
}
