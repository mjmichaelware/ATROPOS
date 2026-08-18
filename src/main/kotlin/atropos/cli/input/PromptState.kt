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
    private val redactionFilter: RedactionFilter = RedactionFilter(),
    historyStore: PromptHistoryRing? = null
) {
    private val line = PromptTextBuffer(maximumBufferLength)
    private val history = historyStore ?: PromptHistoryRing(historyLimit, redactionFilter)
    private val browser = PromptHistoryBrowser(history)
    private val suggestions = PromptSuggestionState()
    private val collapsedPastes = CollapsedPasteRegistry()

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

    fun paletteLevel(): CommandPaletteLevel = suggestions.level(line.textBeforeCursor())

    fun paletteGroup(): String? = suggestions.selectedGroup(line.textBeforeCursor())

    fun paletteCommand(): String? = suggestions.selectedCommand(line.textBeforeCursor())

    fun isPaletteGroupLevel(): Boolean = suggestions.isGroupLevel(line.textBeforeCursor())

    fun clampSuggestionSelection(maximumInclusive: Int) {
        suggestions.clampSelection(maximumInclusive)
    }

    fun apply(event: KeyEvent): PromptEffect = when (event) {
        // Scrollback is the transcript's, not the line's, so these are
        // reported upward untouched rather than editing the prompt.
        is KeyEvent.PageUp -> PromptEffect.Scroll(-SCROLL_LINES)
        is KeyEvent.PageDown -> PromptEffect.Scroll(SCROLL_LINES)
        is KeyEvent.Printable -> insertEvent(event.text)
        is KeyEvent.Paste -> insertEvent(
            // A large paste goes into the registry and a summary goes into the
            // line. The buffer has a length ceiling and the composer redraws on
            // every keystroke, so a pasted Source Document previously either
            // overflowed the line outright or made every subsequent keypress
            // redraw three thousand words.
            if (collapsedPastes.shouldCollapse(event.text)) {
                collapsedPastes.collapse(event.text)
            } else {
                event.text
            }
        )
        is KeyEvent.InvalidInput -> PromptEffect.InputError(event.reason)
        is KeyEvent.UnknownEscape -> PromptEffect.None

        KeyEvent.Enter -> {
            if (suggestions.isGroupLevel(line.textBeforeCursor())) {
                PromptEffect.Redraw
            } else {
                val selected = suggestions.selectedCommand(line.textBeforeCursor())
                if (selected != null) line.replace(selected)
                val committed = commit()
                if (committed.isBlank()) PromptEffect.Redraw else PromptEffect.Submit(committed, mode)
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
            if (!suggestions.collapse(line.textBeforeCursor())) moveLeft()
            PromptEffect.Redraw
        }

        KeyEvent.ArrowRight -> {
            if (!suggestions.expand(line.textBeforeCursor())) moveRight()
            PromptEffect.Redraw
        }

        KeyEvent.ArrowUp -> {
            if (isCommandSuggestionActive()) suggestions.moveSelectionUp(line.textBeforeCursor()) else historyUp()
            PromptEffect.Redraw
        }

        KeyEvent.ArrowDown -> {
            if (isCommandSuggestionActive()) suggestions.moveSelectionDown(line.textBeforeCursor()) else historyDown()
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
        // Expanded before anything else sees it. History records the full text
        // too: a recalled line holding a placeholder would expand to nothing
        // once the registry had been cleared, and would send a prompt with the
        // document silently missing.
        val result = collapsedPastes.expand(line.text)
        if (result.isNotBlank()) {
            val lane = PromptHistoryLane.classify(result)
            lastCommittedLane = lane
            history.record(lane, result)
        }
        clear()
        return result
    }

    /** What the line stands for beyond what it shows, or null when nothing. */
    fun collapsedPasteSummary(): String? =
        collapsedPastes.placeholders()
            .filter { it in line.text }
            .takeIf { it.isNotEmpty() }
            ?.joinToString(" ")

    fun clear() {
        line.clear()
        browser.detach()
        suggestions.reset()
        collapsedPastes.clear()
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

        /**
         * Rows a page key moves.
         *
         * Deliberately less than a full screen, so a few lines of context
         * survive the jump and the operator can tell where they landed.
         */
        const val SCROLL_LINES = 6
    }
}
