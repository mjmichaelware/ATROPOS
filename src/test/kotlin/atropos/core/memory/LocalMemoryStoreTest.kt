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
        assertEquals(64, restored.contentSha256.length)
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

        store.rememberSession("session-1", "session state", "provider=groq goal=bootstrap")
        store.rememberThread("thread-1", "thread state", "continuation thread")
        store.rememberBatch("batch-1", "batch state", "phase=9 restart memory")
        store.rememberJob("job-1", "job state", "status=running")
        store.rememberQueue("queue-1", "queue state", "checkpoint=patch_applied")
        store.rememberRoute("route-1", "route decision", "selected=groq fallback=openrouter")
        store.rememberFailure("repair", "failure-1", "compile failure", "failure signature")
        store.rememberRepair("repair-1", "repair result", "fixed symbol")
        store.rememberVerification("verify-1", "verification result", "passed=true")
        store.rememberSourceDecision("source-1", "source decision", "section=S0011")
        store.rememberToolResult("tool-1", "tool call", "command=deterministic verifier")
        store.rememberSummary("summary-1", "summary state", "phase 9 persisted")
        store.rememberRecovery("recovery-1", "recovery state", "stale lease reclaimed")
        store.rememberReward("narrow", "verification reward +1.0", "scope=narrow exitCode=0 timedOut=false durationMs=42")

        val reopened = LocalMemoryStore(root = root, env = emptyMap())

        assertEquals(14, reopened.status().totalRecords)
        assertEquals("session state", reopened.findBySubject("session", "session-1").first().title)
        assertEquals("thread state", reopened.findBySubject("thread", "thread-1").first().title)
        assertEquals("batch state", reopened.findBySubject("batch", "batch-1").first().title)
        assertEquals("job state", reopened.findBySubject("job", "job-1").first().title)
        assertEquals("queue state", reopened.findBySubject("queue", "queue-1").first().title)
        assertEquals("route decision", reopened.findBySubject("route", "route-1").first().title)
        assertEquals("compile failure", reopened.findBySubject("repair", "failure-1").first().title)
        assertEquals(16, reopened.findBySubject("repair", "failure-1").first().failureSignature?.length)
        assertEquals("repair result", reopened.latestByKind(MemoryKind.REPAIR).first().title)
        assertEquals("verification result", reopened.latestByKind(MemoryKind.VERIFICATION).first().title)
        val sourceRecord = reopened.findBySubject("source", "source-1").first()
        assertEquals("source decision", sourceRecord.title)
        assertEquals("source-1", sourceRecord.sourceCoordinate)
        assertEquals(MemoryAuthority.SOURCE_REFERENCE, sourceRecord.authority)
        assertEquals("summary state", reopened.findBySubject("summary", "summary-1").first().title)
        assertEquals("recovery state", reopened.findBySubject("recovery", "recovery-1").first().title)
        assertEquals("verification reward +1.0", reopened.findBySubject("reward", "narrow").first().title)
        assertTrue(reopened.search("deterministic verifier").any { it.record.kind == MemoryKind.TOOL })
        assertTrue(reopened.search("verification reward").any { it.record.kind == MemoryKind.REWARD })
    }

    @Test
    fun findBySubjectTypes_reads_full_snapshot_before_limiting() {
        val root = Files.createTempDirectory("atropos-memory-subject-types-").toFile()
        val store = LocalMemoryStore(root = root, env = emptyMap())

        store.rememberDetailed(
            kind = MemoryKind.BATCH,
            title = "self-host evaluation",
            body = "goal=shg-1",
            tags = listOf("selfhost"),
            subjectType = "selfhost_dag_eval",
            subjectId = "shg-1"
        )
        repeat(5005) { index ->
            store.rememberDetailed(
                kind = MemoryKind.SUMMARY,
                title = "generic summary $index",
                body = "body $index",
                tags = listOf("generic"),
                subjectType = "summary",
                subjectId = "summary-$index"
            )
        }

        val reopened = LocalMemoryStore(root = root, env = emptyMap())
        val found = reopened.findBySubjectTypes(setOf("selfhost_dag_eval"), limit = 5)

        assertEquals(1, found.size)
        assertEquals("selfhost_dag_eval", found.first().subjectType)
        assertEquals("shg-1", found.first().subjectId)
    }
}
