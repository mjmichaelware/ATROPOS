/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.input

import atropos.core.security.RedactionFilter

/**
 * Turns key events into [PromptEffect]s.
 *
 * This is the state machine only. Everything it used to do inline now has an
 * owner it composes:
 *
 *  - [PromptTextBuffer] — the editable line and its codepoint-safe cursor
 *  - [PromptHistoryRing] — bounded, redacted recall storage
 *  - [PromptHistoryBrowser] — where the operator is inside that storage
 *  - [PromptSuggestionState] — whether the command palette is open
 *  - [PromptHistoryLane] — which recall lane a line belongs to
 *
 * What is left here is the part that genuinely needs all five at once: deciding,
 * for one key, which of them to move and what the caller must then do. The
 * public surface is unchanged, so callers keep using it exactly as before.
 *
 * ## One rule ties the pieces together
 *
 * Editing detaches history. As soon as the operator changes a recalled line it
 * stops being a remembered entry and becomes their own draft, so stepping back
 * down must not overwrite it. That rule is enforced at every mutating entry
 * point here rather than inside the buffer, because the buffer has no idea a
 * history exists — which is exactly why it can be tested on its own.
 */
class PromptState(
    private val historyLimit: Int = DEFAULT_HISTORY_LIMIT,
    private val maximumBufferLength: Int = DEFAULT_MAXIMUM_BUFFER_LENGTH,
    private val redactionFilter: RedactionFilter = RedactionFilter()
) {
    private val line = PromptTextBuffer(maximumBufferLength)
    private val history = PromptHistoryRing(historyLimit, redactionFilter)
    private val browser = PromptHistoryBrowser(history)
    private val suggestions = PromptSuggestionState()

    private var lastCommittedLane = PromptHistoryLane.PROMPT
    private var reverseSearchNeedle = ""

    val cursor: Int get() = line.cursor

    var mode: InputMode = InputMode.ASK
        private set

    val text: String get() = line.text

    init {
        require(historyLimit > 0)
        require(maximumBufferLength > 0)
    }

    fun suggestionSelection(): Int = suggestions.selectionFor(line.textBeforeCursor())

    fun clampSuggestionSelection(maximumInclusive: Int) {
        suggestions.clampSelection(maximumInclusive)
    }

    fun apply(event: KeyEvent): PromptEffect = when (event) {
        is KeyEvent.Printable -> insertEvent(event.text)
        is KeyEvent.Paste -> insertEvent(event.text)
        is KeyEvent.InvalidInput -> PromptEffect.InputError(event.reason)
        is KeyEvent.UnknownEscape -> PromptEffect.None

        KeyEvent.Enter -> {
            val committed = commit()
            if (committed.isBlank()) {
                PromptEffect.Redraw
            } else {
                PromptEffect.Submit(committed, mode)
            }
        }

        KeyEvent.Tab -> PromptEffect.Complete

        KeyEvent.ShiftTab -> {
            toggleMode()
            PromptEffect.ModeChanged(mode)
        }

        KeyEvent.Backspace -> {
            backspace()
            PromptEffect.Redraw
        }

        KeyEvent.Delete -> {
            delete()
            PromptEffect.Redraw
        }

        KeyEvent.ArrowLeft -> {
            moveLeft()
            PromptEffect.Redraw
        }

        KeyEvent.ArrowRight -> {
            moveRight()
            PromptEffect.Redraw
        }

        KeyEvent.ArrowUp -> {
            if (isCommandSuggestionActive()) suggestions.moveSelectionUp() else historyUp()
            PromptEffect.Redraw
        }

        KeyEvent.ArrowDown -> {
            if (isCommandSuggestionActive()) suggestions.moveSelectionDown() else historyDown()
            PromptEffect.Redraw
        }

        KeyEvent.Home -> {
            moveHome()
            PromptEffect.Redraw
        }

        KeyEvent.End -> {
            moveEnd()
            PromptEffect.Redraw
        }

        KeyEvent.CtrlC ->
            if (line.isNotEmpty()) {
                clear()
                PromptEffect.Redraw
            } else {
                PromptEffect.Cancel
            }

        KeyEvent.CtrlD ->
            if (line.isEmpty()) {
                PromptEffect.EndOfInput
            } else {
                delete()
                PromptEffect.Redraw
            }

        KeyEvent.CtrlR -> {
            reverseSearch()
            PromptEffect.Redraw
        }

        KeyEvent.CtrlT -> PromptEffect.None
        KeyEvent.CtrlTab -> PromptEffect.None

        KeyEvent.Escape -> {
            suggestions.dismiss()
            PromptEffect.Redraw
        }
    }

    fun insert(text: String): Boolean {
        if (text.isEmpty()) return true
        if (!line.insert(text)) return false
        browser.detach()
        suggestions.onTextChanged()
        reverseSearchNeedle = ""
        return true
    }

    fun backspace() {
        if (!line.backspace()) return
        browser.detach()
        suggestions.onTextChanged()
    }

    fun delete() {
        if (!line.delete()) return
        browser.detach()
        suggestions.onTextChanged()
    }

    fun moveLeft() {
        line.moveLeft()
    }

    fun moveRight() {
        line.moveRight()
    }

    fun moveHome() {
        line.moveHome()
    }

    fun moveEnd() {
        line.moveEnd()
    }

    fun toggleMode() {
        mode = mode.next()
    }

    fun historyUp() {
        applyMove(browser.up(currentLane(), line.text))
    }

    fun historyDown() {
        applyMove(browser.down())
    }

    /**
     * Records the line in its lane and hands it back.
     *
     * The lane is remembered so that pressing Up on the now-empty prompt recalls
     * from the same lane the operator was last working in, rather than always
     * falling back to prose.
     */
    fun commit(): String {
        val result = line.text
        if (result.isNotBlank()) {
            val lane = PromptHistoryLane.classify(result)
            lastCommittedLane = lane
            history.record(lane, result)
        }
        clear()
        return result
    }

    fun clear() {
        line.clear()
        browser.detach()
        suggestions.reset()
        reverseSearchNeedle = ""
    }

    private fun insertEvent(value: String): PromptEffect =
        if (insert(value)) {
            PromptEffect.Redraw
        } else {
            PromptEffect.InputError("prompt exceeded $maximumBufferLength characters")
        }

    /**
     * Applies a traversal result to the line.
     *
     * A recalled entry resets the palette because the line it replaced is gone;
     * a restored draft does not, because the operator is being handed back the
     * line they were already composing.
     */
    private fun applyMove(move: PromptHistoryMove) {
        when (move) {
            is PromptHistoryMove.None -> Unit
            is PromptHistoryMove.Recalled -> {
                line.replace(move.text)
                suggestions.onTextChanged()
            }
            is PromptHistoryMove.RestoredDraft -> line.replace(move.text)
        }
    }

    private fun reverseSearch() {
        val lane = currentLane()
        if (reverseSearchNeedle.isEmpty()) {
            reverseSearchNeedle = line.text.trim()
        }
        applyMove(browser.search(lane, reverseSearchNeedle))
    }

    /**
     * The lane the current line belongs to.
     *
     * An empty line has no leading token to classify, so the last committed lane
     * stands in — that is the lane the operator was demonstrably working in.
     */
    private fun currentLane(): PromptHistoryLane =
        if (line.isEmpty()) lastCommittedLane else PromptHistoryLane.classify(line.text)

    private fun isCommandSuggestionActive(): Boolean = suggestions.isActive(line.textBeforeCursor())

    private companion object {
        const val DEFAULT_HISTORY_LIMIT = 100
        const val DEFAULT_MAXIMUM_BUFFER_LENGTH = 1024 * 1024
    }
}
