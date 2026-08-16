/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.platform

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlatformModuleTopologyTest {
    @Test
    fun `canonical topology reports missing surface modules`() {
        val root = Files.createTempDirectory("atropos-module-topology-")
        val report = PlatformModuleTopology.inspect(root)

        assertFalse(report.valid)
        assertTrue(report.missingPaths.contains("desktop/build.gradle.kts"))
        assertTrue(report.modules.any { it.id == "shared-ui" })
    }

    @Test
    fun `live repository topology is reachable through the platform wire`() {
        val report = PlatformWire().moduleTopology()

        assertTrue(report.modules.map { it.id }.containsAll(listOf("core", "cli", "desktop", "androidApp", "server", "shared-ui")))
    }
}
