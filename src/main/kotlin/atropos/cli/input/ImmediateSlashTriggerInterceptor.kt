/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.input

/**
 * ATOM 6.1: Immediate Slash Trigger Interception.
 *
 * Intercepts the forward slash character immediately upon the very first keystroke
 * without requiring any subsequent alphabetic characters or space delimiters.
 * Transitions input state instantly to palette navigation mode instead of printing
 * characters to the standard output buffer.
 *
 * ## Research: Zero-Latency Character Interception in POSIX Raw Terminal Mode
 * In canonical POSIX terminal mode, characters are line-buffered and held by the line discipline
 * until a newline (0x0A) or EOF is received. By configuring termios parameters VMIN=1 and VTIME=0
 * in raw non-canonical mode, single-character triggers (0x2F '/') can be intercepted in <1ms
 * across Android Termux, Linux Bash/Zsh, macOS, and Windows ConPTY without waiting for delimiters.
 */
object ImmediateSlashTriggerInterceptor {

    val COMMAND_PREFIX_CHARS: Set<Char> = setOf('/', '!', '@', '#')

    sealed class InterceptionResult {
        data object LaunchPalette : InterceptionResult()
        data class PassThrough(val character: Char) : InterceptionResult()
        data class CommandPrefixed(val prefix: Char, val remainder: String) : InterceptionResult()
        data class NaturalLanguage(val text: String) : InterceptionResult()
        data object Empty : InterceptionResult()
    }

    /**
     * Inspects the first non-whitespace character of input or incoming key event.
     */
    fun firstNonWhitespace(input: CharSequence): Char? {
        for (i in 0 until input.length) {
            val c = input[i]
            if (!c.isWhitespace()) return c
        }
        return null
    }

    /**
     * Evaluates whether [input] triggers the immediate command palette overlay.
     */
    fun isImmediateSlashTrigger(input: String): Boolean {
        if (input == "/") return true
        val trimmed = input.trim()
        return trimmed == "/"
    }

    /**
     * Intercepts an incoming keypress character at index 0 of an empty buffer.
     * When the operator types '/', returns [InterceptionResult.LaunchPalette] immediately.
     */
    fun interceptKeystroke(char: Char, currentBufferLength: Int): InterceptionResult {
        if (currentBufferLength == 0 && char == '/') {
            return InterceptionResult.LaunchPalette
        }
        return InterceptionResult.PassThrough(char)
    }

    /**
     * Predicate for immediate zero-latency keystroke interception on empty buffers.
     */
    fun shouldInterceptRawKeystroke(char: Char, currentBufferLength: Int): Boolean =
        currentBufferLength == 0 && char == '/'

    /**
     * Classifies a full or partial input buffer into an [InterceptionResult].
     */
    fun classify(input: String): InterceptionResult {
        if (input.isEmpty()) return InterceptionResult.Empty
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return InterceptionResult.Empty

        if (trimmed == "/" && input.length == 1) {
            return InterceptionResult.LaunchPalette
        }

        val first = firstNonWhitespace(input)
        return when {
            first != null && first in COMMAND_PREFIX_CHARS -> {
                val prefixIndex = input.indexOf(first)
                val remainder = if (prefixIndex + 1 < input.length) input.substring(prefixIndex + 1) else ""
                InterceptionResult.CommandPrefixed(first, remainder)
            }
            else -> InterceptionResult.NaturalLanguage(input)
        }
    }
}
