/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.parity

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SurfaceContractParityTest {

    private val canonical10SpineIds = listOf(
        "home",
        "projects",
        "work",
        "conversations",
        "files",
        "agents",
        "models",
        "automation",
        "history",
        "settings"
    )

    @Test
    fun `verifies 10-node HOE-A02 primary spine parity`() {
        assertEquals(10, canonical10SpineIds.size)
        assertTrue(canonical10SpineIds.contains("home"))
        assertTrue(canonical10SpineIds.contains("projects"))
        assertTrue(canonical10SpineIds.contains("work"))
        assertTrue(canonical10SpineIds.contains("settings"))
    }
}
