package atropos.core.provider

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SourceContextMetricsTest {
    @Test
    fun `source pack records bounded context saving and tree edit distance`() {
        val metrics = SourceContextMetrics(sourceByteCount = 1_000, packedByteCount = 200, treeEditDistance = 7)

        assertEquals(80.0, metrics.savingPercent)
        assertEquals(7, metrics.treeEditDistance)
        assertTrue(metrics.savingRatio in 0.0..1.0)
    }

    @Test
    fun `empty source cannot claim a saving`() {
        assertEquals(0.0, SourceContextMetrics(0, 0, null).savingPercent)
    }
}
