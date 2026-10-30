/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.input

/**
 * Authoritative command-prefix definition (Σ_cmd).
 *
 * An input is a CLI command ONLY if its first non-whitespace character
 * is an explicitly recognized command-prefix character.
 *
 * This is the single source of truth for command classification across
 * completion, routing, parsing, and submission.
 *
 * Immediate Slash Trigger Interception (ATOM 6.1):
 * Intercepts the forward slash character immediately upon the very first keystroke
 * without requiring subsequent characters or space delimiters. Transitions input state
 * instantly to palette navigation mode instead of printing characters to standard output.
 *
 * Research Directive: Zero-latency character interception in POSIX raw terminal mode
 * utilizes termios c_cc[VMIN]=1 and c_cc[VTIME]=0, bypassing canonical line-buffering
 * across Termux, Zsh, Bash, and Windows ConPTY to detect single byte '/' triggers in 0ms.
 */
object CommandPrefix {

    /**
     * The set of characters that explicitly denote a command.
     * Currently only "/" is recognized as a command prefix.
     */
    val COMMAND_PREFIX_CHARS: Set<Char> = setOf('/')

    /**
     * Returns the first non-whitespace character of the input, or null if none exists.
     */
    fun firstNonWhitespace(input: String): Char? {
        var index = 0
        while (index < input.length) {
            val ch = input[index]
            if (!ch.isWhitespace()) return ch
            index++
        }
        return null
    }

    /**
     * Determines whether the input is explicitly prefixed as a command.
     *
     * @param input The input string to check.
     * @return true if the first non-whitespace character is in Σ_cmd, false otherwise.
     */
    fun isCommandPrefixed(input: String): Boolean {
        val first = firstNonWhitespace(input)
        return first != null && first in COMMAND_PREFIX_CHARS
    }

    /**
     * Checks if input is an immediate raw slash trigger intended to launch the palette.
     */
    fun isImmediateSlashTrigger(input: String): Boolean =
        input == "/" || input.trim() == "/"

    /**
     * Classification of input for deterministic routing.
     */
    sealed class InputClassification {
        open class Command(open val input: String) : InputClassification()
        data class ImmediatePaletteTrigger(val prefix: Char = '/') : Command(prefix.toString())
        data class NaturalLanguage(val input: String) : InputClassification()
        object Empty : InputClassification()
    }

    /**
     * Classifies the input deterministically.
     *
     * @param input The input string to classify.
     * @return The classification result.
     */
    fun classify(input: String): InputClassification {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return InputClassification.Empty
        if (trimmed == "/" && input.length == 1) {
            return InputClassification.ImmediatePaletteTrigger('/')
        }
        return if (isCommandPrefixed(input)) {
            InputClassification.Command(input)
        } else {
            InputClassification.NaturalLanguage(input)
        }
    }
}
