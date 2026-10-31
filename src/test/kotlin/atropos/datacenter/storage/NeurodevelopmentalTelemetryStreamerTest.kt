/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.datacenter.storage

import atropos.datacenter.shipper.HuggingFaceShipper
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class NeurodevelopmentalTelemetryStreamerTest {

    @Test
    fun `HuggingFaceShipper handles empty records and invalid credentials with Soft-Fail Law`() {
        val shipper = HuggingFaceShipper(apiKey = "invalid-token")
        assertTrue(shipper.streamBatch(emptyList()), "Empty batch should be a no-op success")

        // An invalid token must soft-fail without throwing an exception
        val failedResult = shipper.streamBatch(listOf(mapOf("test" to "data")))
        assertFalse(failedResult, "Invalid credentials should return false under Soft-Fail Network Law")
    }

    @Test
    fun `NeurodevelopmentalTelemetryStreamer dispatches under Soft-Fail Law`() {
        val streamer = NeurodevelopmentalTelemetryStreamer()
        val batch = TelemetryStreamBatch(
            batchId = "test-batch-001",
            trajectoryId = "traj-001",
            recordsCount = 1,
            payloadJson = """{"metric":"dopamine","value":1.0}""",
            merkleRoot = "sha256-dummy-root"
        )

        val report = streamer.dispatchAndVerifySync(batch)
        assertNotNull(report)
        assertEquals("test-batch-001", report.batchId)
        assertNotNull(report.verificationNotes)
    }
}
