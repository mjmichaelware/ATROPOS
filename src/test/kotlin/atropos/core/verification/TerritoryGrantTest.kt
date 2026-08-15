/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.verification

import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TerritoryGrantTest {

    @AfterTest
    fun tearDown() {
        TerritoryGrant.clearGrants()
    }

    @Test
    fun `detects drift for out of territory modifications`() {
        val root = System.getProperty("user.dir")
        val allowedDir = File(root, "src/main/kotlin/atropos/core").canonicalPath
        val disallowedDir = File(root, "src/main/kotlin/atropos/cli").canonicalPath

        TerritoryGrant.recordGrant("task_1", listOf(allowedDir))

        val modified = listOf(
            File(allowedDir, "Provider.kt").canonicalPath,
            File(disallowedDir, "CommandRouter.kt").canonicalPath
        )

        val drift = TerritoryGrant.detectDrift("task_1", modified)
        assertEquals(1, drift.size)
        assertTrue(drift[0].contains("CommandRouter.kt"))
    }
}
