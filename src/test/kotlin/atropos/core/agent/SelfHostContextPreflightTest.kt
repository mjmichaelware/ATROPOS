/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.agent

import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertNotNull

class SelfHostContextPreflightTest {

    @Test
    fun testPreflightBasic() {
        val root = Paths.get("/tmp")
        val preflight = SelfHostContextPreflight(root)
        assertNotNull(preflight)
    }
}
