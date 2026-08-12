/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.input

/**
 * Splits a pasted block into the commands it actually contains.
 *
 * [atropos.cli.CommandLexer] treats a newline as ordinary whitespace, which is
 * correct for one command spanning two lines and catastrophic for two commands
 * pasted together: the whole block lexes into a single token list, the router
 * dispatches on the first token, and every remaining line is silently consumed
 * as arguments to it. A paste of
 *
 * ```
 * /thinking 2
 * /self-host run build the thing
 * ```
 *
 * set the thinking depth and discarded the self-host run without a word. On a
 * phone, pasting a multi-line block is the primary way an operator states a
 * real task, so this was not an edge case.
 *
 * ## Where a command begins
 *
 * A new command starts only at a line whose first token is a **registered
 * command family**. Not merely a line starting with `/` — prose and pasted
 * documents are full of things like `/usr/bin/env` and `and/or`, and splitting
 * on those would tear a natural-language prompt into fragments, which is a
 * worse failure than the one being fixed because it would corrupt input that
 * used to work.
 *
 * The registry is the authority on what a command is, exactly as it is for
 * `/help` and for local NL resolution. One owner, three consumers.
 *
 * Every other line is a continuation of the command above it. That is what
 * lets a multi-line natural-language argument survive:
 *
 * ```
 * /self-host run Add narration to the queue path.
 * Today it shows a spinner and nothing else.
 * ```
 *
 * is one command whose argument spans two lines, because `Today` is not a
 * command family.
 */
object PastedInputSplitter {

    /**
     * @return the commands in order. A single-line input returns itself, so
     *   the ordinary typing path is unchanged.
     */
    fun split(
        input: String,
        families: Set<String> = CommandRegistry.families()
    ): List<String> {
        if (!input.contains('\n')) return listOf(input)

        val lines = input.lines()
        val commands = mutableListOf<StringBuilder>()

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue

            if (startsCommand(trimmed, families) || commands.isEmpty()) {
                commands += StringBuilder(trimmed)
            } else {
                // Joined with a space rather than a newline: the lexer would
                // treat either the same, and a space keeps the echoed command
                // readable on one line.
                commands.last().append(' ').append(trimmed)
            }
        }

        return commands.map { it.toString() }.filter { it.isNotBlank() }.ifEmpty { listOf(input) }
    }

    /**
     * Whether this line opens a new command.
     *
     * `!` is included because a bang line is shell execution, which is never a
     * continuation of anything — swallowing one into the previous command's
     * arguments would hide the fact that a shell command was pasted at all.
     */
    private fun startsCommand(trimmedLine: String, families: Set<String>): Boolean {
        if (trimmedLine.startsWith("!")) return true
        if (!trimmedLine.startsWith("/")) return false
        val head = trimmedLine.substringBefore(' ').lowercase()
        return head in families
    }
}
