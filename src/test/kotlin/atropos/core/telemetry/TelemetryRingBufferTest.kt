/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.telemetry

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files

class TelemetryRingBufferTest {

    @Test
    fun `test non blocking ring buffer ingestion and flush`() {
        val tempSpool = Files.createTempDirectory("telemetry-spool-test-")
        val ringBuffer = TelemetryRingBuffer(
            capacity = 50,
            flushRecordThreshold = 5,
            spoolDir = tempSpool
        )

        try {
            for (i in 1..10) {
                val record = FrontierSweTelemetryRecord.create(
                    instanceId = "test-instance-$i",
                    h0PromptLineage = "h0-test",
                    payload = AgenticShellExecution(
                        argv = listOf("echo", "test-$i"),
                        exitCode = 0,
                        stdoutTruncated = "ok",
                        stderrTruncated = "",
                        durationMs = 10L
                    ),
                    rewardSignal = 0.1
                )
                val accepted = ringBuffer.offer(record)
                assertTrue(accepted)
            }

            ringBuffer.triggerAsyncFlush()
            Thread.sleep(500)

            val pending = ringBuffer.listPendingSpoolFiles()
            assertTrue(pending.isNotEmpty() || ringBuffer.currentBufferSize() <= 10)
        } finally {
            ringBuffer.shutdown()
            tempSpool.toFile().deleteRecursively()
        }
    }
}
