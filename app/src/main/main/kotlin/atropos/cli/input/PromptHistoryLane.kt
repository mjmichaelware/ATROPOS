/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.input

/**
 * Which recall lane an input line belongs to.
 *
 * Prompts, slash commands, and shell lines are recalled separately on purpose.
 * A single flat history makes the arrow keys useless the moment an operator
 * mixes kinds: pressing Up after typing `/` should offer the last slash command,
 * not the paragraph of prose typed before it.
 *
 * The taxonomy lives here rather than inside the prompt state machine because
 * classification is a pure question about a string — it needs no cursor, no
 * buffer, and no key event — and keeping it separate is what lets it be tested
 * directly and reused by anything that needs to bucket an input line.
 */
enum class PromptHistoryLane {
    /** Ordinary natural-language input. */
    PROMPT,

    /** A slash command such as `/status`. */
    SLASH,

    /** A shell escape (`!…`) or one of the shell-ish slash commands. */
    SHELL;

    companion object {

        /**
         * Slash commands that act on the working directory or the process
         * environment rather than on ATROPOS state.
         *
         * They share the shell lane because that is where an operator looks for
         * them: someone who just ran `/git status` and reaches for Up is
         * recalling a shell action, not a slash command in the `/status` sense.
         */
        private val SHELL_COMMANDS = listOf("/shell", "/pwd", "/cd", "/ls", "/git")

        /** Prefix that escapes straight to the shell. */
        private const val SHELL_ESCAPE = "!"

        /**
         * Named with a suffix because a companion constant called `SLASH` would
         * shadow the enum entry of the same name inside this scope, and
         * `-> SLASH` would silently mean the string rather than the lane.
         */
        private const val SLASH_PREFIX = "/"

        /**
         * Buckets a line by its leading token.
         *
         * Leading whitespace is ignored so a pasted or indented line lands in
         * the same lane it would have landed in when typed.
         */
        fun classify(value: String): PromptHistoryLane {
            val trimmed = value.trimStart()
            return when {
                trimmed.startsWith(SHELL_ESCAPE) -> SHELL
                SHELL_COMMANDS.any { trimmed.startsWith(it) } -> SHELL
                trimmed.startsWith(SLASH_PREFIX) -> SLASH
                else -> PROMPT
            }
        }
    }
}
