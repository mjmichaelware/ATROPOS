/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.thinking

import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PipelineNarratorTest {

    private val narrator = PipelineNarrator("atomize")

    @BeforeTest
    @AfterTest
    fun clearStream() = Thinking.stream.clear()

    private fun texts(depth: ThinkingDepth) = Thinking.stream.replay(depth).map { it.text }

    @Test
    fun a_count_is_phrased_as_a_count() {
        narrator.counted("atoms decoded", 390, of = 593)

        assertEquals(listOf("390 of 593 atoms decoded"), texts(ThinkingDepth.L2))
    }

    @Test
    fun a_count_with_no_population_omits_it_rather_than_inventing_one() {
        narrator.counted("characters read", 46_379)

        assertEquals(listOf("46379 characters read"), texts(ThinkingDepth.L2))
    }

    @Test
    fun the_shape_of_the_run_is_L2_and_the_detail_is_L3() {
        // `/thinking 2` must stay readable on a four-hundred-atom document,
        // which means per-atom lines cannot be in it. `/thinking 3` promises
        // everything, and this is the everything.
        narrator.stage("atomizing")
        narrator.counted("atoms", 3)
        narrator.item(1, 3, "a1", "first")
        narrator.lookup("lakehouse", "provider registry", hits = 2)

        assertEquals(listOf("atomizing", "3 atoms"), texts(ThinkingDepth.L2))
        assertEquals(4, texts(ThinkingDepth.L3).size)
    }

    @Test
    fun an_empty_lookup_says_nothing_rather_than_zero() {
        narrator.lookup("lakehouse", "unmatched", hits = 0)
        narrator.lookup("lakehouse", "matched", hits = 1)

        assertEquals(
            listOf("lakehouse ← unmatched: nothing", "lakehouse ← matched: 1 hit"),
            texts(ThinkingDepth.L3)
        )
    }

    @Test
    fun a_long_query_is_clipped_so_the_trace_stays_a_column() {
        narrator.lookup("lakehouse", "x".repeat(500), hits = 1)

        assertTrue(texts(ThinkingDepth.L3).single().length < 120)
    }

    @Test
    fun an_artifact_reports_the_size_on_disk_and_not_the_one_intended() {
        val path = Files.createTempFile("atropos-narrator", ".json")
        Files.writeString(path, "0123456789")

        narrator.artifact("spec handoff", path)

        assertTrue(texts(ThinkingDepth.L3).single().contains("10 bytes"), texts(ThinkingDepth.L3).toString())
    }

    @Test
    fun a_missing_artifact_says_so() {
        // The more useful of the two answers. A stage reporting the file it
        // meant to write is reporting its intention, and an operator asking
        // "did it write the handoff?" wants the disk's answer.
        val path = Files.createTempDirectory("atropos-narrator").resolve("absent.json")

        narrator.artifact("spec handoff", path)

        assertTrue(texts(ThinkingDepth.L3).single().endsWith("(MISSING)"))
    }

    @Test
    fun every_narrator_carries_its_own_category() {
        Narrate.atomize.stage("a")
        Narrate.lakehouse.stage("b")

        assertEquals(
            listOf("atomize", "lakehouse"),
            Thinking.stream.replay(ThinkingDepth.L2).map { it.category }
        )
    }

    @Test
    fun narration_never_fails_the_work_it_describes() {
        // A subscriber that throws must not take down the run it was watching.
        val unsubscribe = Thinking.stream.subscribe { error("renderer exploded") }
        try {
            narrator.stage("still fine")
            narrator.counted("atoms", 1)
        } finally {
            unsubscribe()
        }

        assertEquals(2, texts(ThinkingDepth.L2).size)
    }
}
