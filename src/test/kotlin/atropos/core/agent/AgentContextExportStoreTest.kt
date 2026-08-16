package atropos.core.agent

import java.nio.file.Files
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertTrue

class AgentContextExportStoreTest {
    @Test
    fun export_contains_bounded_phone_handoff_commands() {
        val root = Files.createTempDirectory("atropos-context-export-")
        val record = AgentJobRecord(
            id = "job-1",
            task = "export context",
            status = AgentJobStatus.COMPLETED,
            provider = "local",
            createdAt = Instant.EPOCH,
            updatedAt = Instant.EPOCH,
            startedAt = Instant.EPOCH,
            metaFile = root.resolve("job-1.meta")
        )

        val path = AgentContextExportStore(root).write(record, listOf("src/main/kotlin/atropos/Main.kt"))
        val text = Files.readString(path)
        assertTrue(text.contains("next context export command:"))
        assertTrue(text.contains("/sdcard/Download/atropos-context-export.txt"))
        assertTrue(text.contains("context export media scan: termux-media-scan"))
    }
}
