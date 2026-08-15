// SPDX-License-Identifier: AGPL-3.0-only
package atropos.core.intent

import kotlin.test.*

class MentionExtractorTest {
    
    @Test
    fun `extracts multiple mentions correctly`() {
        val extractor = MentionExtractor(setOf("book", "ATROPOS"))
        val input = "Analyze @book and @unknown"
        val mentions = extractor.extractMentions(input)
        
        assertEquals(2, mentions.size)
        
        assertEquals("@book", mentions[0].token)
        assertEquals("book", mentions[0].resolvedPath)
        
        assertEquals("@unknown", mentions[1].token)
        assertNull(mentions[1].resolvedPath)
    }

    @Test
    fun `handles empty input`() {
        val extractor = MentionExtractor(setOf("book", "ATROPOS"))
        val mentions = extractor.extractMentions("")
        assertEquals(0, mentions.size)
    }
}
