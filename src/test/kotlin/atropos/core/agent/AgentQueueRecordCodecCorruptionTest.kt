/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.agent

import atropos.core.security.RedactionFilter
import java.nio.file.Files
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AgentQueueRecordCodecCorruptionTest {
    @Test
    fun malformed_persisted_record_is_visible_as_corrupt_not_idle() {
        val entries = Files.createTempDirectory("atropos-queue-corrupt-")
        val meta = entries.resolve("queue-1.meta")
        Files.writeString(meta, "id=queue-1\nstate=NOT_A_QUEUE_STATE\n")

        val codec = AgentQueueRecordCodec(
            entriesDir = entries,
            clock = { Instant.parse("2026-08-16T00:00:00Z") },
            redactionFilter = RedactionFilter()
        )

        val record = codec.parse(meta)

        assertEquals(AgentQueueState.CORRUPT, record.state)
        assertTrue(record.terminal)
        assertNotNull(record.corruptReason)
        assertTrue(record.corruptReason!!.contains("malformed queue record"))
        assertEquals("inspect ${meta}", record.nextCommand())
    }
}
