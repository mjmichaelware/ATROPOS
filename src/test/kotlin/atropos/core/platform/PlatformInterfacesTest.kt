/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.platform

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlatformInterfacesTest {

    @Test
    fun `stubs return default implementation results`() {
        assertNotNull(ComposeDesktopStub)
        assertNotNull(ComposeIosStub)
        assertFalse(GraalVmConfig.isNativeImage)
        assertEquals("http://localhost:8080", KtorBackendConfig.getBackendUrl())
    }

    @Test
    fun `SwarmMdLoader returns refused when file is absent`() {
        val file = File("non-existent-swarm.md")
        val result = SwarmMdLoader.loadSwarmConfig(file)
        assertTrue(result.startsWith("REFUSED"))
    }

    @Test
    fun `AggregateProgressCalculator parses registry counts`() {
        val tempFile = Files.createTempFile("reg-", ".json").toFile()
        tempFile.writeText("""
            {
                "obligations": [
                    { "obligationId": "1", "status": "WRITTEN" },
                    { "obligationId": "2", "status": "NOT_WRITTEN" }
                ]
            }
        """.trimIndent())

        val pct = AggregateProgressCalculator.calculatePercentage(tempFile)
        assertEquals(50.0, pct)
        tempFile.delete()
    }

    private fun assertNotNull(obj: Any) {
        assertTrue(obj != null)
    }
}
