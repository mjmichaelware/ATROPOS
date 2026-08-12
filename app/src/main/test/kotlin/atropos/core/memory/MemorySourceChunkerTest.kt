/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.memory

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MemorySourceChunkerTest {

    @Test
    fun testChunkText() {
        val chunker = MemorySourceChunker(windowTokens = 5)
        val text = "This is a simple test text that is going to be chunked into multiple pieces for testing."
        val chunks = chunker.chunk(text)
        
        assertTrue(chunks.isNotEmpty())
        chunks.forEach { chunk ->
            assertTrue(chunk.text.length > 0)
        }
    }
}
