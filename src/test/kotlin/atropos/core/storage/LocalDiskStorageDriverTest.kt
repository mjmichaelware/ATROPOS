/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.storage

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.time.Instant

class LocalDiskStorageDriverTest {
    @Test
    fun testDiskDriverOperations() {
        val tempDir = File.createTempFile("disk", "driver").apply { delete(); mkdirs() }
        try {
            val driver = LocalDiskStorageDriver(tempDir)
            val blob = BlobObject(
                "doc1", 
                11L, 
                Instant.now(), 
                "rule1", 
                { ByteArrayInputStream("hello world".toByteArray()) }
            )
            
            assertTrue(driver.write(blob))
            
            val readBlob = driver.read("doc1")
            assertNotNull(readBlob)
            assertEquals("hello world", String(readBlob!!.contentStream().readBytes()))
            
            val metadata = driver.listAllMetadata()
            assertEquals(1, metadata.size)
            assertEquals("doc1", metadata[0].first)
            
            assertTrue(driver.delete("doc1"))
            assertNull(driver.read("doc1"))
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
