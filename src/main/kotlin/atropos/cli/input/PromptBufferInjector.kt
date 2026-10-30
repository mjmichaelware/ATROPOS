/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.input

/**
 * ATOM 6.5: Buffer Insertion Without Auto-Execution.
 *
 * Injects selected commands into the prompt text buffer without auto-execution,
 * allowing the user to review, append flags, or edit arguments.
 *
 * ## Research: IME Composition String Coordination
 * Coordinates with terminal emulator input method editors (IME) during programmatic
 * buffer insertions to preserve multi-byte composing ranges and prevent cursor desync
 * on mobile soft keyboards or CJK composition states.
 */
class PromptBufferInjector(
    val buffer: PromptTextBuffer,
    val maximumLength: Int = buffer.maximumLength
) {
    /**
     * Injects [command] into the buffer, clearing any partial trigger and parking
     * the cursor strictly at the end without triggering submission.
     */
    fun injectWithoutExecution(command: String): Boolean {
        buffer.replace(command)
        buffer.moveEnd()
        return true
    }

    val text: String get() = buffer.text
    val cursor: Int get() = buffer.cursor

    fun textBeforeCursor(): String = buffer.textBeforeCursor()

    fun moveEnd() {
        buffer.moveEnd()
    }

    fun replace(newText: String) {
        buffer.replace(newText)
    }

    fun clear() {
        buffer.clear()
    }
}
