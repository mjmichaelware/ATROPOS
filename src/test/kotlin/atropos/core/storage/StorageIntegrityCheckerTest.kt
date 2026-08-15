/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.storage

import kotlin.test.*

import java.io.ByteArrayInputStream
import java.io.File
import java.time.Instant

class StorageIntegrityCheckerTest {
    @Test
    fun testChecksumVerification() {
        val tempDir = File.createTempFile("integrity", "test").apply { delete(); mkdirs() }
        try {
            val driver = LocalDiskStorageDriver(tempDir)
            val content = "test content"
            val blob = BlobObject(
                "obj1", 
                content.length.toLong(), 
                Instant.now(), 
                "rule", 
                { ByteArrayInputStream(content.toByteArray()) }
            )
            driver.write(blob)
            
            val checker = StorageIntegrityChecker(driver)
            // SHA-256 of "test content" is 6ae8a75555209fd6c44157c0aed8016e763ff435a19cf186f76863140143ff72
            val correctHash = "6ae8a75555209fd6c44157c0aed8016e763ff435a19cf186f76863140143ff72"
            
            assertTrue(checker.verifyChecksum("obj1", correctHash))
            assertFalse(checker.verifyChecksum("obj1", "wronghash"))
            assertFalse(checker.verifyChecksum("nonexistent", correctHash))
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
