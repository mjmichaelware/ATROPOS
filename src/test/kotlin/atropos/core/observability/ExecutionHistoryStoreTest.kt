/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.observability

import atropos.cli.ui.design.RunState
import atropos.core.journal.EventCategory
import atropos.core.journal.EventJournalService
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Source Doc 3 §5.3 has three clauses and each can fail independently: the
 * seven filter axes, surviving restart, and answering "without loading the
 * entire trace into memory". The last is the one a test can accidentally pass
 * by reading everything and filtering, so it is asserted on the read count
 * rather than on the result.
 */
class ExecutionHistoryStoreTest {

    private fun fixture(): Triple<Path, EventPublisher, ExecutionHistoryStore> {
        val root = Files.createTempDirectory("atropos-history-")
        val journal = EventJournalService(repoRoot = root)
        val publisher = EventPublisher(journal = journal, stream = ProvenanceStream())
        return Triple(root, publisher, ExecutionHistoryStore(repoRoot = root))
    }

    private fun seed(publisher: EventPublisher, runId: String = "run-1") {
        publisher.publish(
            runId, ExecutionRole.DIRECTOR, EventCategory.DAG, RunState.PLANNING,
            "decomposed into 4 nodes", task = "plan work", provider = "gemini",
            source = "InternalExecutionDagSynthesizer.kt", requirement = "SD3#5.3@L70-70"
        )
        publisher.publish(
            runId, ExecutionRole.WORKER, EventCategory.COMMAND, RunState.RUNNING,
            "./gradlew compileKotlin", task = "compile", provider = "groq",
            source = "src/main/kotlin/atropos/core/observability/HistoryIndex.kt"
        )
        publisher.publish(
            runId, ExecutionRole.WORKER, EventCategory.TEST, RunState.RUNNING,
            "19 tests passed", task = "verify", provider = "local",
            source = "ExecutionHistoryStoreTest.kt"
        )
        publisher.publish(
            runId, ExecutionRole.AUDITOR, EventCategory.ERROR, RunState.FAILED,
            "territory violation", task = "audit", provider = "local",
            source = "TerritoryEnforcer.kt", requirement = "SD3#5.3@L70-70"
        )
    }

    @Test
    fun `history survives restart because it was never only in memory`() {
        val (root, publisher, _) = fixture()
        seed(publisher)

        // A brand-new store, as a restarted process would build.
        val restarted = ExecutionHistoryStore(repoRoot = root)

        assertEquals(4, restarted.search("run-1", HistoryQuery()).events.size)
    }

    @Test
    fun `record extension appends through the journal and rebuilds the index`() {
        val root = Files.createTempDirectory("atropos-history-record-")
        val store = ExecutionHistoryStore(repoRoot = root)
        store.record(
            ExecutionEvent(
                sequence = 999L,
                timestamp = Instant.now(),
                role = ExecutionRole.WORKER,
                category = EventCategory.COMMAND,
                state = RunState.COMPLETE,
                payload = "recorded command",
                provider = "local",
                task = "record",
                source = "ExecutionHistoryStoreTest.kt",
                runId = "record-run"
            )
        )

        val restarted = ExecutionHistoryStore(repoRoot = root)
        assertEquals("recorded command", restarted.search("record-run", HistoryQuery()).events.single().payload)
    }

    @Test
    fun `history index is the explicit seekable index owner`() {
        val (root, publisher, _) = fixture()
        seed(publisher)

        val index = HistoryIndex(root.resolve(".atropos/runs"))
        val entries = index.load("run-1")

        assertEquals(4, entries.size)
        assertTrue(entries.all { it.byteOffset >= 0L })
    }

    @Test
    fun `filter by agent`() {
        val (_, publisher, store) = fixture()
        seed(publisher)

        val result = store.search("run-1", HistoryQuery.byAgent(ExecutionRole.AUDITOR))

        assertEquals(1, result.events.size)
        assertEquals(ExecutionRole.AUDITOR, result.events.single().role)
    }

    @Test
    fun `filter by provider`() {
        val (_, publisher, store) = fixture()
        seed(publisher)

        assertEquals(1, store.search("run-1", HistoryQuery(provider = "gemini")).events.size)
        assertEquals(2, store.search("run-1", HistoryQuery(provider = "local")).events.size)
    }

    @Test
    fun `filter by task`() {
        val (_, publisher, store) = fixture()
        seed(publisher)

        assertEquals(1, store.search("run-1", HistoryQuery(task = "compile")).events.size)
    }

    @Test
    fun `filter by file, matched as a substring an operator would actually type`() {
        val (_, publisher, store) = fixture()
        seed(publisher)

        assertEquals(1, store.search("run-1", HistoryQuery.touching("HistoryIndex")).events.size)
        assertEquals(1, store.search("run-1", HistoryQuery.touching("historyindex")).events.size)
    }

    @Test
    fun `filter by test`() {
        val (_, publisher, store) = fixture()
        seed(publisher)

        val result = store.search("run-1", HistoryQuery.tests())

        assertEquals(1, result.events.size)
        assertEquals(EventCategory.TEST, result.events.single().category)
    }

    @Test
    fun `filter by error`() {
        val (_, publisher, store) = fixture()
        seed(publisher)

        val result = store.search("run-1", HistoryQuery.failures())

        assertEquals(1, result.events.size)
        assertTrue(result.events.single().payload.contains("territory violation"))
    }

    @Test
    fun `filter by event type`() {
        val (_, publisher, store) = fixture()
        seed(publisher)

        val result = store.search("run-1", HistoryQuery(categories = setOf(EventCategory.DAG, EventCategory.TEST)))

        assertEquals(2, result.events.size)
    }

    @Test
    fun `filters combine with and`() {
        val (_, publisher, store) = fixture()
        seed(publisher)

        val result = store.search(
            "run-1",
            HistoryQuery(roles = setOf(ExecutionRole.WORKER), categories = setOf(EventCategory.TEST))
        )

        assertEquals(1, result.events.size)
    }

    /**
     * The clause that matters most and is easiest to fake. A query matching one
     * event of 400 must read one journal line, not 400.
     */
    @Test
    fun `a narrow query reads only the matching lines`() {
        val (_, publisher, store) = fixture()
        repeat(100) { seed(publisher) }

        val result = store.search("run-1", HistoryQuery.failures())

        assertEquals(400, result.scanned, "the whole index is scanned, which is cheap")
        assertEquals(100, result.matched)
        assertEquals(100, result.events.size, "only matching journal lines are read")
    }

    @Test
    fun `a result explains why it matched`() {
        val (_, publisher, store) = fixture()
        seed(publisher)

        val explanation = store.search("run-1", HistoryQuery.byAgent(ExecutionRole.AUDITOR)).explain()

        assertTrue(explanation.contains("agent in [auditor]"))
        assertTrue(explanation.contains("matched 1 of 4 indexed"))
    }

    @Test
    fun `a limit truncates and says so rather than silently dropping`() {
        val (_, publisher, store) = fixture()
        seed(publisher)

        val result = store.search("run-1", HistoryQuery(limit = 2))

        assertEquals(2, result.events.size)
        assertTrue(result.truncated)
        assertTrue(result.explain().contains("truncated at limit 2"))
    }

    @Test
    fun `search spans runs, newest first`() {
        val (root, publisher, store) = fixture()
        seed(publisher, "run-1")
        Thread.sleep(1100)
        seed(publisher, "run-2")

        val result = store.searchAll(HistoryQuery.failures())

        assertEquals(2, result.events.size)
        assertEquals(2, store.runIds().size)
        assertEquals("run-2", store.runIds().first(), "newest run first")
        assertTrue(Files.isDirectory(root.resolve(".atropos/runs")))
    }

    @Test
    fun `a stale index is rebuilt rather than trusted`() {
        val (root, publisher, store) = fixture()
        seed(publisher)
        assertEquals(4, store.search("run-1", HistoryQuery()).events.size)

        // More events after the index was written.
        seed(publisher)

        assertEquals(8, store.search("run-1", HistoryQuery()).events.size)
    }

    @Test
    fun `a corrupt index is rebuilt rather than throwing`() {
        val (root, publisher, store) = fixture()
        seed(publisher)
        store.search("run-1", HistoryQuery())

        val indexFile = root.resolve(".atropos/runs/run-1/events.index")
        Files.writeString(indexFile, "this is not an index\nnor is this\n")
        Files.setLastModifiedTime(indexFile, Files.getLastModifiedTime(indexFile))

        val result = store.search("run-1", HistoryQuery())
        assertTrue(result.events.isNotEmpty(), "a bad index must not make history unreadable")
    }

    @Test
    fun `an unknown run is empty rather than an error`() {
        val (_, _, store) = fixture()

        val result = store.search("never-existed", HistoryQuery())

        assertEquals(emptyList(), result.events)
        assertEquals(0, result.scanned)
    }

    @Test
    fun `a non-ascii payload survives the seek and re-read`() {
        val (_, publisher, store) = fixture()
        publisher.publish(
            "run-1", ExecutionRole.WORKER, EventCategory.STDOUT, RunState.RUNNING,
            "built ünïcödé — 完了", task = "build", provider = "local", source = "x.kt"
        )
        publisher.publish(
            "run-1", ExecutionRole.WORKER, EventCategory.ERROR, RunState.FAILED,
            "after the multibyte line", task = "build", provider = "local", source = "x.kt"
        )

        val failures = store.search("run-1", HistoryQuery.failures())

        assertEquals(1, failures.events.size)
        assertTrue(
            failures.events.single().payload.contains("after the multibyte line"),
            "byte offsets must be byte offsets, not character counts"
        )
    }

    @Test
    fun `export reuses the same read path as search`() {
        val (_, publisher, store) = fixture()
        seed(publisher)

        val export = store.exportRun("run-1")

        assertEquals(4, export.eventCount)
        assertTrue(export.failures().isNotEmpty())
        assertFalse(MarkdownExporter().export(export).isBlank())
    }
}
