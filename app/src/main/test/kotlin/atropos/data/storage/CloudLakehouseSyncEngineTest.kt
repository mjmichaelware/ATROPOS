/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.data.storage

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CloudLakehouseSyncEngineTest {

    @Test
    fun testStoreAndRetrieveContent() {
        val tempDir = Files.createTempDirectory("lakehouse-test-").toFile()
        try {
            val engine = CloudLakehouseSyncEngine(storageDir = tempDir)
            val content = "hello lakehouse".toByteArray()
            val hash = engine.storeContentAddressed(content)
            
            assertTrue(hash.isNotEmpty())
            
            val retrieved = engine.retrieveContent(hash)
            assertNotNull(retrieved)
            assertEquals("hello lakehouse", String(retrieved))
            
            val missing = engine.missingHashes(listOf(hash, "0000000000000000000000000000000000000000000000000000000000000000"))
            assertEquals(1, missing.size)
            assertEquals("0000000000000000000000000000000000000000000000000000000000000000", missing[0])
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
