/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.input

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A multi-line paste used to execute only its first command and silently drop
 * the rest — the router dispatched on the first token and the lexer, which
 * treats a newline as whitespace, handed it everything else as arguments.
 *
 * The tests below hold both halves of the fix: a block of commands splits, and
 * a multi-line natural-language argument does not.
 */
class PastedInputSplitterTest {

    private val families = setOf("/thinking", "/self-host", "/status", "/exit", "/agent")

    @Test
    fun `a single line is returned unchanged`() {
        assertEquals(listOf("/status"), PastedInputSplitter.split("/status", families))
    }

    @Test
    fun `two pasted commands become two commands`() {
        val split = PastedInputSplitter.split("/thinking 2\n/status", families)

        assertEquals(listOf("/thinking 2", "/status"), split)
    }

    @Test
    fun `a multi-line argument stays with its command`() {
        val split = PastedInputSplitter.split(
            """
            /self-host run Add narration to the queue path.
            Today it shows a spinner and nothing else,
            which is the same defect the run path had.
            """.trimIndent(),
            families
        )

        assertEquals(1, split.size)
        assertEquals(
            "/self-host run Add narration to the queue path. " +
                "Today it shows a spinner and nothing else, " +
                "which is the same defect the run path had.",
            split.single()
        )
    }

    @Test
    fun `the case that was broken splits correctly`() {
        val split = PastedInputSplitter.split(
            """
            /thinking 2
            /self-host run Add live thinking narration to the agent queue run path.
            Today /agent queue run next shows a spinner line and then nothing.
            """.trimIndent(),
            families
        )

        assertEquals(2, split.size)
        assertEquals("/thinking 2", split[0])
        assertEquals(
            "/self-host run Add live thinking narration to the agent queue run path. " +
                "Today /agent queue run next shows a spinner line and then nothing.",
            split[1],
            "a command family mentioned mid-sentence is prose, not a new command"
        )
    }

    @Test
    fun `an unregistered slash word does not start a command`() {
        val split = PastedInputSplitter.split(
            "/self-host run look at\n/usr/bin/env and report what you find",
            families
        )

        assertEquals(1, split.size, "a path is not a command family")
    }

    @Test
    fun `pure prose across lines stays one prompt`() {
        val split = PastedInputSplitter.split(
            "build me a todo app\nwith a dark theme\nand offline sync",
            families
        )

        assertEquals(listOf("build me a todo app with a dark theme and offline sync"), split)
    }

    @Test
    fun `a bang line always starts a new command`() {
        val split = PastedInputSplitter.split("/status\n!ls -la", families)

        assertEquals(listOf("/status", "!ls -la"), split)
    }

    @Test
    fun `blank lines are dropped rather than becoming empty commands`() {
        val split = PastedInputSplitter.split("/thinking 2\n\n\n/status\n", families)

        assertEquals(listOf("/thinking 2", "/status"), split)
    }

    @Test
    fun `prose before the first command keeps its own entry`() {
        val split = PastedInputSplitter.split("do the thing\n/status", families)

        assertEquals(listOf("do the thing", "/status"), split)
    }

    @Test
    fun `the live registry recognises the real families`() {
        val split = PastedInputSplitter.split("/thinking 2\n/help")

        assertEquals(listOf("/thinking 2", "/help"), split)
    }
}
