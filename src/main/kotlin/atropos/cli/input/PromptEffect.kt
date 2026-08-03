/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.input

/**
 * What the caller must do after a key was applied to the prompt.
 *
 * The prompt state machine draws nothing and runs nothing itself. It reports an
 * effect and the caller performs it, which is what keeps a key-handling change
 * from silently becoming a rendering or execution change.
 */
sealed class PromptEffect {

    /** Nothing to do; the frame on screen is still correct. */
    object None : PromptEffect()

    /** The line changed; repaint it. */
    object Redraw : PromptEffect()

    /** Tab: run completion against the current line. */
    object Complete : PromptEffect()

    /** Ctrl-C on an empty line: abandon the current operation. */
    object Cancel : PromptEffect()

    /** Ctrl-D on an empty line: the input stream is finished. */
    object EndOfInput : PromptEffect()

    /** Enter on a non-blank line: hand [text] to the router in [mode]. */
    data class Submit(
        val text: String,
        val mode: InputMode
    ) : PromptEffect()

    /** Shift-Tab: the agency mode changed and the status surface should say so. */
    data class ModeChanged(
        val mode: InputMode
    ) : PromptEffect()

    /**
     * The keystroke was refused, with a reason fit to show the operator.
     *
     * Distinct from [None] because a refusal has to be visible. A paste that
     * silently did nothing reads as a broken terminal.
     */
    data class InputError(
        val message: String
    ) : PromptEffect()
}
