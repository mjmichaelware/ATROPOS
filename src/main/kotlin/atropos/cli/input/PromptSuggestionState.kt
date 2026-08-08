/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.input

/**
 * Whether the command palette is open, and which row is highlighted.
 *
 * Split out of the prompt state machine because it answers one question the
 * rest of that machine does not care about: given the text left of the cursor,
 * should a suggestion list be on screen, and if so where is the selection.
 *
 * ## Why "active" is recomputed rather than stored
 *
 * The palette's visibility is a function of the current line, not a mode the
 * operator toggles. Storing it as a flag means every edit path has to remember
 * to recompute it, and the one that forgets leaves a palette open over a line
 * that no longer looks like a command. [isActive] therefore derives the answer
 * from the text each time it is asked, and the only stored state is the parts
 * that genuinely cannot be derived: the highlighted row, and whether the
 * operator dismissed the palette with Escape.
 *
 * A suggestion is offered only for a single leading token — no whitespace, no
 * newline. Once a line has arguments the operator is filling in a command they
 * have already chosen, and re-opening the palette over it is noise.
 */
class PromptSuggestionState(
    private val matcher: (String) -> Boolean = { CommandRegistry.search(it).isNotEmpty() }
) {
    private var selection = 0
    private var dismissed = false
    private var paletteMode = false
    private val palette = CommandPaletteNavigator()

    /**
     * True when a palette should be visible for [textBeforeCursor].
     *
     * The text is trimmed at the start so a leading space does not suppress the
     * palette, but interior whitespace does — that marks the end of the token.
     */
    fun isActive(textBeforeCursor: String): Boolean {
        if (dismissed) return false
        val token = textBeforeCursor.trimStart()
        if (token.isEmpty()) return false
        if (token.any(Char::isWhitespace) || token.contains('\n')) return false
        return matcher(token)
    }

    /** The highlighted row, or 0 when no palette is showing. */
    fun selectionFor(textBeforeCursor: String): Int =
        if (isActive(textBeforeCursor)) {
            if (isHelpPalette(textBeforeCursor)) palette.selection.index else selection
        } else 0

    fun moveSelectionUp(textBeforeCursor: String = "") {
        if (isHelpPalette(textBeforeCursor)) {
            paletteMode = true
            palette.move(-1)
        }
        else selection = (selection - 1).coerceAtLeast(0)
    }

    fun moveSelectionDown(textBeforeCursor: String = "") {
        if (isHelpPalette(textBeforeCursor)) {
            paletteMode = true
            palette.move(1)
        }
        else selection++
    }

    /**
     * Keeps the highlight inside a list that may have shrunk.
     *
     * The renderer knows how many rows survived filtering; this state does not,
     * so the bound arrives from outside rather than being assumed here.
     */
    fun clampSelection(maximumInclusive: Int) {
        if (paletteMode && palette.selection.level == CommandPaletteLevel.GROUPS) {
            palette.move(0)
        } else {
            selection = selection.coerceIn(0, maximumInclusive.coerceAtLeast(0))
        }
    }

    fun expand(textBeforeCursor: String): Boolean {
        if (!isActive(textBeforeCursor) || !isHelpPalette(textBeforeCursor)) return false
        paletteMode = true
        palette.right()
        return true
    }

    fun collapse(textBeforeCursor: String): Boolean {
        if (!isActive(textBeforeCursor) || !isHelpPalette(textBeforeCursor)) return false
        paletteMode = true
        palette.left()
        return true
    }

    fun level(textBeforeCursor: String): CommandPaletteLevel =
        if (isHelpPalette(textBeforeCursor)) palette.selection.level else CommandPaletteLevel.COMMANDS

    fun selectedGroup(textBeforeCursor: String): String? =
        if (isHelpPalette(textBeforeCursor)) palette.selection.group else null

    fun selectedCommand(textBeforeCursor: String): String? =
        if (isHelpPalette(textBeforeCursor)) palette.selection.command else null

    fun isGroupLevel(textBeforeCursor: String): Boolean =
        isActive(textBeforeCursor) && level(textBeforeCursor) == CommandPaletteLevel.GROUPS

    /** Escape: hide the palette until the line changes again. */
    fun dismiss() {
        dismissed = true
        selection = 0
        paletteMode = false
    }

    /**
     * Any edit to the line reopens the palette.
     *
     * Dismissal is scoped to the line as typed. Once the operator types another
     * character they are composing something new, and the palette should be
     * allowed to help with it.
     */
    fun onTextChanged() {
        selection = 0
        dismissed = false
        paletteMode = false
        palette.reset()
    }

    fun reset() {
        selection = 0
        dismissed = false
        paletteMode = false
        palette.reset()
    }

    private fun isHelpPalette(textBeforeCursor: String): Boolean =
        textBeforeCursor.trim().lowercase() in setOf("?", "/?", "/help", "/usage", "help", "usage")
}
