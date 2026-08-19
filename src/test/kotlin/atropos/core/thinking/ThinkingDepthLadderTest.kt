/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.thinking

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The depth ladder, asserted as a ladder.
 *
 * `/thinking 1`, `2` and `3` are a promise about volume as much as content:
 * each level shows everything the level below it showed, plus more. Individual
 * call sites are tested for choosing the right depth; nothing tested that the
 * levels stay ordered as narration was added across the pipeline, and the
 * failure mode is silent — a stage that reports at L2 what belongs at L3 makes
 * `/thinking 2` gradually unusable on a large document without any test going
 * red.
 */
class ThinkingDepthLadderTest {

    @BeforeTest
    @AfterTest
    fun clearStream() = Thinking.stream.clear()

    private fun emitAcrossThePipeline() {
        // A run's worth of narration in the shapes the pipeline actually uses.
        Narrate.ingest.stage("reading the document")
        Narrate.ingest.counted("characters read", 45_518)
        Narrate.atomize.headlineCount("atoms decoded", 390, of = 390)
        repeat(20) { index ->
            Narrate.atomize.item(index + 1, 20, "atom-$index", "an obligation")
            Narrate.dimension.item(index + 1, 20, "atom-$index", "6 of 16 dimensions apply")
            Narrate.lakehouse.lookup("lakehouse", "provider registry", hits = index % 3)
        }
        Narrate.plan.headlineCount("nodes planned", 390)
        Narrate.plan.counted("edges between them", 108)
        Narrate.evidence.skipped("atoms.json", "not in the verified manifest")
        Narrate.provider.confidence("atom-1", 0.82, "two independent sources")
    }

    @Test
    fun each_level_contains_everything_the_level_below_it_showed() {
        emitAcrossThePipeline()

        val l1 = Thinking.stream.replay(ThinkingDepth.L1).toSet()
        val l2 = Thinking.stream.replay(ThinkingDepth.L2).toSet()
        val l3 = Thinking.stream.replay(ThinkingDepth.L3).toSet()

        assertTrue(l2.containsAll(l1), "L2 dropped something L1 showed")
        assertTrue(l3.containsAll(l2), "L3 dropped something L2 showed")
    }

    @Test
    fun the_levels_are_strictly_more_verbose_and_not_merely_equal() {
        emitAcrossThePipeline()

        val l1 = Thinking.stream.replay(ThinkingDepth.L1).size
        val l2 = Thinking.stream.replay(ThinkingDepth.L2).size
        val l3 = Thinking.stream.replay(ThinkingDepth.L3).size

        assertTrue(l1 < l2, "L1 and L2 showed the same $l1 lines; the level does nothing")
        assertTrue(l2 < l3, "L2 and L3 showed the same $l2 lines; the level does nothing")
    }

    @Test
    fun per_item_narration_stays_out_of_the_shape_of_the_run() {
        // Several thousand per-atom lines is exactly what `/thinking 3`
        // promises and exactly what would drown `/thinking 2`.
        emitAcrossThePipeline()

        val l2 = Thinking.stream.replay(ThinkingDepth.L2).map(StreamedThought::text)

        assertTrue(
            l2.none { it.startsWith("[") },
            "per-item lines reached L2: " + l2.filter { it.startsWith("[") }.take(3)
        )
        assertTrue(l2.any { it.contains("390") }, "L2 lost the counts that are its whole job")
    }

    @Test
    fun a_stage_that_says_nothing_is_visible_as_nothing() {
        // An empty trace at L3 means a stage that never spoke, which is a
        // finding. It must not be papered over with filler.
        assertEquals(0, Thinking.stream.replay(ThinkingDepth.L3).size)
    }

    @Test
    fun every_narrator_carries_a_category_so_the_trace_can_be_read_in_columns() {
        emitAcrossThePipeline()

        val uncategorised = Thinking.stream.replay(ThinkingDepth.L3).filter { it.category.isBlank() }

        assertTrue(uncategorised.isEmpty(), "these lines cannot be filtered: $uncategorised")
    }
}
