package atropos.core.execution

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LocalWorkQueueTest {
    @Test
    fun enqueue_and_run_redact_arbitrary_command_arguments_and_output_before_persistence_or_rendering() {
        val root = Files.createTempDirectory("atropos-work-queue-").toFile()
        val queue = LocalWorkQueue(root, env = emptyMap())
        val secretArgument = "api-token-12345"
        val secretOutput = "output-token-67890"

        val enqueued = queue.enqueue("secret command", listOf("sh", "-c", "printf '$secretOutput'", secretArgument))
        val persistedAfterEnqueue = File(root, "queue.jsonl").readText()
        val result = queue.runNext()
        val persistedAfterRun = File(root, "queue.jsonl").readText()

        assertEquals(listOf("[REDACTED]", "[REDACTED]", "[REDACTED]", "[REDACTED]"), enqueued.command)
        assertEquals(0, result?.exitCode)
        assertEquals("[REDACTED]", result?.outputTail)
        assertEquals("[REDACTED]", result?.item?.lastOutputTail)
        assertFalse(persistedAfterEnqueue.contains(secretArgument))
        assertFalse(persistedAfterEnqueue.contains(secretOutput))
        assertFalse(persistedAfterRun.contains(secretArgument))
        assertFalse(persistedAfterRun.contains(secretOutput))
        assertTrue(persistedAfterRun.contains("[REDACTED]"))
    }

    @Test
    fun reloaded_queue_fails_without_executing_redacted_command_arguments() {
        val root = Files.createTempDirectory("atropos-work-queue-").toFile()
        val secret = "never-run-after-reload"
        LocalWorkQueue(root, env = emptyMap()).enqueue("restart", listOf("sh", "-c", "printf '$secret'"))

        val result = LocalWorkQueue(root, env = emptyMap()).runNext()

        assertEquals(125, result?.exitCode)
        assertEquals(WorkStatus.FAILED, result?.item?.status)
        assertFalse(result?.outputTail.orEmpty().contains(secret))
        assertFalse(File(root, "queue.jsonl").readText().contains(secret))
    }

    @Test
    fun codec_redacts_raw_command_and_output_values() {
        val encoded = WorkItemCodec.encode(
            WorkItem("job-1", "label", listOf("secret-argument"), WorkStatus.FAILED, 1, 1, 1, 1, "secret-output")
        )

        assertFalse(encoded.contains("secret-argument"))
        assertFalse(encoded.contains("secret-output"))
        assertTrue(encoded.contains("[REDACTED]"))
    }
}
