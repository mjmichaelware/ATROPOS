/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.memory

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The end-to-end form of the defect that took memory out on a live run.
 *
 * ```
 * st_memory=SKIPPED_SOFT_FAIL:stackoverflowerror
 * lt_memory=SKIPPED_SOFT_FAIL:stackoverflowerror
 * ```
 *
 * Decoding a stored record used a pattern that recursed once per character of
 * each field, so a record large enough threw `StackOverflowError` out of
 * `search`. `FactoryResearchService` catches it, records a soft failure and
 * carries on — which meant every factory run planned with zero recall while
 * still reporting `confidence=100`.
 *
 * The store's own tests all used short records and passed throughout.
 */
class LargeMemoryRecallTest {

    private fun store() = LocalMemoryStore(
        root = Files.createTempDirectory("atropos-memory-").toFile(),
        env = emptyMap()
    )

    @Test
    fun `a record far larger than the stack can be written and recalled`() {
        val store = store()
        val body = "deterministic verification evidence. ".repeat(8_000)

        store.remember(MemoryKind.NOTE, "large recall probe", body, listOf("probe"))

        val hits = store.search("verification", limit = 10)

        assertEquals(1, hits.size, "the large record must come back")
        assertEquals("large recall probe", hits.first().record.title)
    }

    @Test
    fun `many large records recall without exhausting the stack`() {
        val store = store()
        val body = "territory evidence gate ".repeat(4_000)
        repeat(25) { index -> store.remember(MemoryKind.NOTE, "probe $index", body, listOf("bulk")) }

        val hits = store.search("territory", limit = 50)

        assertEquals(25, hits.size)
    }

    @Test
    fun `a record whose body is full of quotes and backslashes round-trips`() {
        val store = store()
        val body = """he said "no" and the path was C:\Users\x""".repeat(2_000)

        val written = store.remember(MemoryKind.NOTE, "escape probe", body)
        val recalled = store.search("path", limit = 5)

        assertEquals(1, recalled.size)
        assertEquals(written.body, recalled.first().record.body, "escaping must survive the round trip")
    }

    @Test
    fun `tags containing a bracket do not truncate the tag list`() {
        val store = store()

        store.remember(MemoryKind.NOTE, "bracket probe", "body", listOf("a]b", "second", "third"))

        val record = store.search("bracket", limit = 5).first().record
        assertEquals(listOf("a]b", "second", "third"), record.tags)
    }

    @Test
    fun `recall of a large store finishes promptly`() {
        val store = store()
        val body = "coordination efficiency ".repeat(3_000)
        repeat(40) { index -> store.remember(MemoryKind.NOTE, "timing $index", body) }

        val started = System.nanoTime()
        store.search("coordination", limit = 20)
        val elapsedMs = (System.nanoTime() - started) / 1_000_000

        assertTrue(elapsedMs < 10_000, "recall over 40 large records took ${elapsedMs}ms")
    }
}
