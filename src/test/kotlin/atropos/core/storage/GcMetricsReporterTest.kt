/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.storage

import kotlin.test.*

import java.time.Instant

class GcMetricsReporterTest {
    @Test
    fun testMetricsAggregation() {
        val reporter = GcMetricsReporter()
        
        reporter.recordPass(GcPassResult("wm1", Instant.now(), 100, 10, 5000L, 120L))
        reporter.recordPass(GcPassResult("wm2", Instant.now(), 50, 5, 2000L, 80L))
        
        assertEquals(2, reporter.getHistory().size)
        assertEquals(7000L, reporter.getTotalBytesFreed())
    }
}
