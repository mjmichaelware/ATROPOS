package atropos.data.indexer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LatentOntologicalIndexerTest {
    @Test
    fun `diagnostic similarity is deterministic and is not a DLOI fallback`() {
        val indexer = LatentOntologicalIndexer("build/test-latent-indexer.log")
        assertEquals(1.0, indexer.computeCosineSimilarity(listOf(1.0, 0.0), listOf(1.0, 0.0)))
        assertTrue(indexer.queryTopMatches(listOf(1.0, 0.0)).isEmpty())
    }
}
