package atropos.core.memory

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class LocalMemoryStoreTest {
    @Test
    fun persistsAcrossRestartAndRedactsSecrets() {
        val root = Files.createTempDirectory("atropos-memory-test-").toFile()
        val store = LocalMemoryStore(root = root, env = emptyMap())

        val record = store.rememberDetailed(
            kind = MemoryKind.ROUTE,
            title = "provider route",
            body = "Authorization: Bearer sk-test-secret-value",
            tags = listOf("route", "test"),
            subjectType = "route",
            subjectId = "job-1"
        )

        assertTrue(record.body.contains("<redacted>") || record.body.contains("<redacted:bearer>"))

        val reopened = LocalMemoryStore(root = root, env = emptyMap())
        val restored = reopened.findBySubject("route", "job-1").firstOrNull()
        assertNotNull(restored)
        assertEquals(record.id, restored.id)
        assertFalse(restored.body.contains("sk-test-secret-value"))
        assertEquals(1, reopened.status().totalRecords)
    }

    @Test
    fun reportsCorruptLinesAndCompacts() {
        val root = Files.createTempDirectory("atropos-memory-corrupt-").toFile()
        val store = LocalMemoryStore(root = root, env = emptyMap())
        store.remember(MemoryKind.NOTE, "one", "body one")
        store.remember(MemoryKind.NOTE, "two", "body two")

        val jsonl = root.toPath().resolve("memory.jsonl")
        Files.writeString(jsonl, jsonl.readText(StandardCharsets.UTF_8) + "{broken\n", StandardCharsets.UTF_8)

        val reopened = LocalMemoryStore(root = root, env = emptyMap())
        val statusBefore = reopened.status()
        assertEquals(2, statusBefore.totalRecords)
        assertEquals(1, statusBefore.corruptRecords)

        val compacted = reopened.compact(1)
        assertEquals(1, compacted.totalRecords)
        assertEquals(1, reopened.all().size)
    }

    @Test
    fun restartSmokeKeepsRouteFailureRepairVerificationAndToolStateQueryable() {
        val root = Files.createTempDirectory("atropos-memory-restart-").toFile()
        val store = LocalMemoryStore(root = root, env = emptyMap())

        store.rememberRoute("route-1", "route decision", "selected=groq fallback=openrouter")
        store.rememberFailure("repair", "failure-1", "compile failure", "failure signature")
        store.rememberRepair("repair-1", "repair result", "fixed symbol")
        store.rememberVerification("verify-1", "verification result", "passed=true")
        store.rememberToolResult("tool-1", "tool call", "command=deterministic verifier")

        val reopened = LocalMemoryStore(root = root, env = emptyMap())

        assertEquals(5, reopened.status().totalRecords)
        assertEquals("route decision", reopened.findBySubject("route", "route-1").first().title)
        assertEquals("compile failure", reopened.findBySubject("repair", "failure-1").first().title)
        assertEquals("repair result", reopened.latestByKind(MemoryKind.REPAIR).first().title)
        assertEquals("verification result", reopened.latestByKind(MemoryKind.VERIFICATION).first().title)
        assertTrue(reopened.search("deterministic verifier").any { it.record.kind == MemoryKind.TOOL })
    }
}
