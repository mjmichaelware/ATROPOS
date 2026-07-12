package atropos.core.custodian

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CustodianServiceTest {
    @Test
    fun cleanTempFilesRemovesTmpFiles() {
        val dir = Files.createTempDirectory("custodian-test-")
        val atroposDir = dir.resolve(".atropos")
        Files.createDirectories(atroposDir)
        val tmpFile = atroposDir.resolve("data.tmp")
        Files.writeString(tmpFile, "test data")

        val svc = CustodianService(dir)
        val report = svc.cleanTempFiles()
        assertTrue(report.actions.any { it.target.contains("data.tmp") })
        assertTrue(report.totalBytesFreed > 0)
    }

    @Test
    fun noTempFilesReturnsEmptyReport() {
        val dir = Files.createTempDirectory("custodian-empty-")
        val svc = CustodianService(dir)
        val report = svc.cleanTempFiles()
        assertEquals(0, report.actions.size)
    }
}
