/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.telemetry

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ZeroInventionMetricsCollectorTest {

    @Test
    fun `records turn telemetry and generates accurate quantitative snapshots`() {
        val collector = ZeroInventionMetricsCollector(Path.of("."))

        collector.recordTurnMetrics("issue-1", 4000, true)
        collector.recordTurnMetrics("issue-2", 6000, false)
        collector.recordInventionAnomaly("B-PROV-007", "Spec violation prevented")

        val snap = collector.generateSnapshot()
        assertEquals(2, snap.totalIssuesAttempted)
        assertEquals(1, snap.issuesResolved)
        assertEquals(0.5, snap.passRate)
        assertEquals(10000L, snap.totalTokensConsumed)
        assertEquals(5000L, snap.averageTokensPerIssue)
        assertEquals(1, snap.totalInventionsLogged)
    }
}
