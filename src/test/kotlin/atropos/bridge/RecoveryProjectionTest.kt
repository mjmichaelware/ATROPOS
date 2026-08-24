package atropos.bridge

import atropos.bridge.projection.RecoveryProjection
import atropos.core.recovery.StateSnapshot
import kotlin.test.Test
import kotlin.test.assertTrue
import java.time.Instant

class RecoveryProjectionTest {
    @Test
    fun unavailable_snapshot_is_explicit_and_does_not_claim_recovery() {
        val json = RecoveryProjection().render(null)
        assertTrue(json.contains("\"available\":false"))
        assertTrue(json.contains("recovery-snapshot-not-wired"))
    }

    @Test
    fun snapshot_payload_exposes_ribbon_counts_from_existing_state() {
        val snapshot = StateSnapshot(
            id = "snapshot-1",
            capturedAt = Instant.parse("2026-08-23T00:00:00Z"),
            goalRuns = emptyList(), dags = emptyList(), worktrees = emptyList(),
            memoryRecords = 0
        )
        val json = RecoveryProjection().render(snapshot)
        assertTrue(json.contains("\"available\":true"))
        assertTrue(json.contains("\"restored\":0"))
        assertTrue(json.contains("\"failed\":0"))
    }
}
