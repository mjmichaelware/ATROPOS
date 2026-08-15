/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.phase20

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.Instant

class Phase20AuditLogTest {
    @Test
    fun testAppendAndRetrieve() {
        val log = Phase20AuditLog()
        val event = AuditEvent(
            "evt-1", "prop-1", Instant.now(), "SUBMIT", "agent-x", "SUCCESS", "sig-123"
        )
        
        log.append(event)
        
        val retrieved = log.getLogForProposal("prop-1")
        assertEquals(1, retrieved.size)
        assertEquals("evt-1", retrieved[0].eventId)
        
        val empty = log.getLogForProposal("prop-2")
        assertTrue(empty.isEmpty())
    }
}
