/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.bridge

import atropos.bridge.http.HttpRequest
import atropos.bridge.queue.ConversationWorkRunner
import atropos.bridge.queue.QueueEntryView
import atropos.bridge.queue.QueueRunOutcome
import atropos.core.security.RedactionFilter
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class BridgeEvidenceHandlerTest {

    private class FakeWorkRunner(val evidencePath: String?) : ConversationWorkRunner {
        override fun list(limit: Int): List<QueueEntryView> = emptyList()
        override fun find(id: String): QueueEntryView? {
            if (id == "q-1") {
                return QueueEntryView(
                    id = "q-1",
                    task = "test-task",
                    state = "RUNNING",
                    checkpoint = "COMPILE",
                    attempts = 1,
                    maxAttempts = 3,
                    terminal = false,
                    failureReason = null,
                    evidence = evidencePath,
                    createdAt = "now",
                    updatedAt = "now"
                )
            }
            return null
        }
        override fun run(id: String?): QueueRunOutcome = QueueRunOutcome.NothingToRun("")
        override fun cancel(id: String, reason: String): QueueEntryView? = null
        override fun throttled(): Boolean = false
    }

    @Test
    fun test_successful_evidence_retrieval_and_redaction() {
        val tempDir = Files.createTempDirectory("evidence-test-")
        try {
            val evidenceFile = tempDir.resolve("evidence.txt")
            val secret = "sk-live-1234567890abcdefghijklmnopqrstuvwxyz"
            Files.writeString(evidenceFile, "Log content with API key: $secret\nSome other evidence here.")

            val work = FakeWorkRunner("evidence.txt")
            val handler = BridgeEvidenceHandler(work, repoRoot = tempDir)
            
            val request = HttpRequest("GET", "/v1/evidence", mapOf("id" to "q-1"), emptyMap(), "")
            val response = handler.getEvidence(request)

            assertEquals(200, response.status)
            assertTrue(response.body.contains("Log content with API key:"))
            assertFalse(response.body.contains(secret), "API key must be redacted")
            assertTrue(response.body.contains("[REDACTED]"))
            assertTrue(response.body.contains("\"truncated\":false"))
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }

    @Test
    fun test_path_escaping_is_refused() {
        val tempDir = Files.createTempDirectory("evidence-test-")
        try {
            val work = FakeWorkRunner("../escaping.txt")
            val handler = BridgeEvidenceHandler(work, repoRoot = tempDir)

            val request = HttpRequest("GET", "/v1/evidence", mapOf("id" to "q-1"), emptyMap(), "")
            val response = handler.getEvidence(request)

            assertEquals(403, response.status)
            assertTrue(response.body.contains("access-denied"))
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }

    @Test
    fun test_truncation_of_large_evidence() {
        val tempDir = Files.createTempDirectory("evidence-test-")
        try {
            val evidenceFile = tempDir.resolve("large_evidence.txt")
            val largeContent = "A".repeat(110_000)
            Files.writeString(evidenceFile, largeContent)

            val work = FakeWorkRunner("large_evidence.txt")
            val handler = BridgeEvidenceHandler(work, repoRoot = tempDir)

            val request = HttpRequest("GET", "/v1/evidence", mapOf("id" to "q-1"), emptyMap(), "")
            val response = handler.getEvidence(request)

            assertEquals(200, response.status)
            assertTrue(response.body.contains("\"truncated\":true"))
            assertTrue(response.body.contains("TRUNCATED: evidence file is larger than"))
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }
}
