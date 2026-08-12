/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.thinking

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * A long run has to be watchable while it runs.
 *
 * The defect these cover: a fourteen-minute self-host run printed one spinner
 * line and nothing else, so progress and a hang looked identical and the only
 * available action destroyed the run.
 */
class ThinkingStreamTest {

    @Test
    fun `a subscriber sees thoughts as they are emitted`() {
        val stream = ThinkingStream()
        val seen = mutableListOf<String>()
        stream.subscribe { seen += it.text }

        stream.emit(ThinkingDepth.L1, "starting")
        stream.emit(ThinkingDepth.L2, "advancing")

        assertEquals(listOf("starting", "advancing"), seen)
    }

    @Test
    fun `unsubscribing stops delivery`() {
        val stream = ThinkingStream()
        val seen = mutableListOf<String>()
        val cancel = stream.subscribe { seen += it.text }

        stream.emit(ThinkingDepth.L1, "before")
        cancel()
        stream.emit(ThinkingDepth.L1, "after")

        assertEquals(listOf("before"), seen)
    }

    @Test
    fun `a subscriber that throws does not take the run down`() {
        val stream = ThinkingStream()
        val survived = mutableListOf<String>()
        stream.subscribe { error("renderer exploded") }
        stream.subscribe { survived += it.text }

        stream.emit(ThinkingDepth.L1, "still running")

        assertEquals(listOf("still running"), survived)
    }

    @Test
    fun `replay is filtered by depth and is additive`() {
        val stream = ThinkingStream()
        stream.emit(ThinkingDepth.L1, "outline")
        stream.emit(ThinkingDepth.L2, "reasoning")
        stream.emit(ThinkingDepth.L3, "trace")

        assertEquals(listOf("outline"), stream.replay(ThinkingDepth.L1).map { it.text })
        assertEquals(listOf("outline", "reasoning"), stream.replay(ThinkingDepth.L2).map { it.text })
        assertEquals(
            listOf("outline", "reasoning", "trace"),
            stream.replay(ThinkingDepth.L3).map { it.text }
        )
    }

    @Test
    fun `the stream is bounded so a long run cannot exhaust a phone`() {
        val stream = ThinkingStream(bound = 10)

        repeat(100) { stream.emit(ThinkingDepth.L1, "line $it") }

        val retained = stream.replay(ThinkingDepth.L3)
        assertEquals(10, retained.size)
        assertEquals("line 99", retained.last().text, "the tail is what an operator reads")
    }

    @Test
    fun `blank thoughts are not published`() {
        val stream = ThinkingStream()
        val seen = mutableListOf<String>()
        stream.subscribe { seen += it.text }

        stream.emit(ThinkingDepth.L1, "   ")

        assertTrue(seen.isEmpty())
    }

    @Test
    fun `narrated steps are still a plain list of what happened`() {
        val stream = ThinkingStream()
        val steps = NarratedSteps(stream)

        steps.outline("started")
        steps += "advanced"
        steps.detail("git status clean")

        assertEquals(listOf("started", "advanced", "git status clean"), steps.frozen())
        assertEquals(3, steps.size)
    }

    @Test
    fun `narrated steps publish at the depth their kind implies`() {
        val stream = ThinkingStream()
        val steps = NarratedSteps(stream)

        steps.outline("milestone")
        steps += "reasoning"
        steps.detail("trace")

        assertEquals(listOf("milestone"), stream.replay(ThinkingDepth.L1).map { it.text })
        assertEquals(listOf("milestone", "reasoning"), stream.replay(ThinkingDepth.L2).map { it.text })
        assertEquals(3, stream.replay(ThinkingDepth.L3).size)
    }

    @Test
    fun `one surface deepening never moves another`() {
        val channels = ThinkingChannels()

        channels.expand("cli", ThinkingDepth.L3)

        assertEquals(ThinkingDepth.L3, channels.depthFor("cli"))
        assertEquals(ThinkingDepth.L1, channels.depthFor("bridge"))
    }

    @Test
    fun `depth filtering never hides a line the operator was already reading`() {
        val stream = ThinkingStream()
        stream.emit(ThinkingDepth.L1, "outline")
        stream.emit(ThinkingDepth.L2, "reasoning")

        val atTwo = stream.replay(ThinkingDepth.L2).map { it.text }
        val atThree = stream.replay(ThinkingDepth.L3).map { it.text }

        assertTrue(atThree.containsAll(atTwo), "expanding must only ever add")
        assertFalse(atTwo.contains("nothing"))
    }
}
