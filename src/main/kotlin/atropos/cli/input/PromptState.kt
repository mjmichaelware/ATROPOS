/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.input

enum class InputMode {
    ASK,
    PLAN,
    AUTOPILOT
}

sealed class PromptEffect {
    object None : PromptEffect()
    object Redraw : PromptEffect()
    object Complete : PromptEffect()
    object Cancel : PromptEffect()
    object EndOfInput : PromptEffect()

    data class Submit(
        val text: String,
        val mode: InputMode
    ) : PromptEffect()

    data class ModeChanged(
        val mode: InputMode
    ) : PromptEffect()

    data class InputError(
        val message: String
    ) : PromptEffect()
}

private enum class HistoryLane {
    PROMPT,
    SLASH,
    SHELL
}

class PromptState(
    private val historyLimit: Int = 100,
    private val maximumBufferLength: Int =
        1024 * 1024
) {
    private val buffer = StringBuilder()
    private val histories = mutableMapOf(
        HistoryLane.PROMPT to mutableListOf<String>(),
        HistoryLane.SLASH to mutableListOf<String>(),
        HistoryLane.SHELL to mutableListOf<String>()
    )

    private var historyIndex = -1
    private var savedBuffer = ""
    private var activeHistoryLane: HistoryLane? = null
    private var lastCommittedLane = HistoryLane.PROMPT
    private var slashSelection = 0
    private var slashDismissed = false
    private var reverseSearchNeedle = ""

    var cursor: Int = 0
        private set

    var mode: InputMode = InputMode.ASK
        private set

    val text: String
        get() = buffer.toString()

    init {
        require(historyLimit > 0)
        require(maximumBufferLength > 0)
    }

    fun suggestionSelection(): Int =
        if (isSlashSuggestionActive()) slashSelection else 0

    fun clampSuggestionSelection(maximumInclusive: Int) {
        slashSelection = slashSelection.coerceIn(
            0,
            maximumInclusive.coerceAtLeast(0)
        )
    }

    fun apply(event: KeyEvent): PromptEffect {
        return when (event) {
            is KeyEvent.Printable ->
                insertEvent(event.text)

            is KeyEvent.Paste ->
                insertEvent(event.text)

            is KeyEvent.InvalidInput ->
                PromptEffect.InputError(event.reason)

            is KeyEvent.UnknownEscape ->
                PromptEffect.None

            KeyEvent.Enter -> {
                val committed = commit()

                if (committed.isBlank()) {
                    PromptEffect.Redraw
                } else {
                    PromptEffect.Submit(
                        committed,
                        mode
                    )
                }
            }

            KeyEvent.Tab ->
                PromptEffect.Complete

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
                if (isSlashSuggestionActive()) {
                    slashSelection =
                        (slashSelection - 1).coerceAtLeast(0)
                } else {
                    historyUp()
                }

                PromptEffect.Redraw
            }

            KeyEvent.ArrowDown -> {
                if (isSlashSuggestionActive()) {
                    slashSelection++
                } else {
                    historyDown()
                }

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

            KeyEvent.CtrlC -> {
                if (buffer.isNotEmpty()) {
                    clear()
                    PromptEffect.Redraw
                } else {
                    PromptEffect.Cancel
                }
            }

            KeyEvent.CtrlD -> {
                if (buffer.isEmpty()) {
                    PromptEffect.EndOfInput
                } else {
                    delete()
                    PromptEffect.Redraw
                }
            }

            KeyEvent.CtrlR -> {
                reverseSearch()
                PromptEffect.Redraw
            }

            KeyEvent.Escape -> {
                slashDismissed = true
                slashSelection = 0
                PromptEffect.Redraw
            }
        }
    }

    fun insert(text: String): Boolean {
        if (text.isEmpty()) return true

        if (buffer.length + text.length >
            maximumBufferLength
        ) {
            return false
        }

        detachHistory()
        buffer.insert(cursor, text)
        cursor += text.length
        slashSelection = 0
        slashDismissed = false
        reverseSearchNeedle = ""
        return true
    }

    fun backspace() {
        if (cursor <= 0) return

        detachHistory()

        val previous = Character.offsetByCodePoints(
            buffer,
            cursor,
            -1
        )

        buffer.delete(previous, cursor)
        cursor = previous
        slashSelection = 0
        slashDismissed = false
    }

    fun delete() {
        if (cursor >= buffer.length) return

        detachHistory()

        val next = Character.offsetByCodePoints(
            buffer,
            cursor,
            1
        )

        buffer.delete(cursor, next)
        slashSelection = 0
        slashDismissed = false
    }

    fun moveLeft() {
        if (cursor <= 0) return

        cursor = Character.offsetByCodePoints(
            buffer,
            cursor,
            -1
        )
    }

    fun moveRight() {
        if (cursor >= buffer.length) return

        cursor = Character.offsetByCodePoints(
            buffer,
            cursor,
            1
        )
    }

    fun moveHome() {
        cursor = 0
    }

    fun moveEnd() {
        cursor = buffer.length
    }

    fun toggleMode() {
        mode = when (mode) {
            InputMode.ASK ->
                InputMode.PLAN

            InputMode.PLAN ->
                InputMode.AUTOPILOT

            InputMode.AUTOPILOT ->
                InputMode.ASK
        }
    }

    fun historyUp() {
        val lane = currentHistoryLane()
        val history = histories.getValue(lane)

        if (history.isEmpty()) return

        if (
            historyIndex == -1 ||
            activeHistoryLane != lane
        ) {
            savedBuffer = buffer.toString()
            historyIndex = -1
            activeHistoryLane = lane
        }

        if (historyIndex < history.lastIndex) {
            historyIndex++
        }

        loadHistory(lane)
    }

    fun historyDown() {
        val lane =
            activeHistoryLane ?: currentHistoryLane()

        when {
            historyIndex > 0 -> {
                historyIndex--
                loadHistory(lane)
            }

            historyIndex == 0 -> {
                historyIndex = -1
                activeHistoryLane = null
                buffer.clear()
                buffer.append(savedBuffer)
                cursor = buffer.length
            }
        }
    }

    fun commit(): String {
        val result = buffer.toString()

        if (result.isNotBlank()) {
            val lane = classify(result)
            lastCommittedLane = lane
            val history = histories.getValue(lane)

            if (history.lastOrNull() != result) {
                history += result

                while (history.size > historyLimit) {
                    history.removeAt(0)
                }
            }
        }

        clear()
        return result
    }

    fun clear() {
        buffer.clear()
        cursor = 0
        historyIndex = -1
        savedBuffer = ""
        activeHistoryLane = null
        slashSelection = 0
        slashDismissed = false
        reverseSearchNeedle = ""
    }

    private fun insertEvent(
        value: String
    ): PromptEffect {
        return if (insert(value)) {
            PromptEffect.Redraw
        } else {
            PromptEffect.InputError(
                "prompt exceeded " +
                    "$maximumBufferLength characters"
            )
        }
    }

    private fun loadHistory(
        lane: HistoryLane
    ) {
        val history = histories.getValue(lane)
        if (historyIndex == -1) return

        val sourceIndex =
            history.lastIndex - historyIndex

        if (sourceIndex !in history.indices) return

        buffer.clear()
        buffer.append(history[sourceIndex])
        cursor = buffer.length
        slashSelection = 0
        slashDismissed = false
    }

    private fun detachHistory() {
        if (historyIndex == -1) return

        historyIndex = -1
        activeHistoryLane = null
        savedBuffer = ""
    }

    private fun reverseSearch() {
        val lane = currentHistoryLane()
        val history = histories.getValue(lane)

        if (history.isEmpty()) return

        if (reverseSearchNeedle.isEmpty()) {
            reverseSearchNeedle =
                buffer.toString().trim()
        }

        val match = history.asReversed().firstOrNull {
            reverseSearchNeedle.isEmpty() ||
                it.contains(
                    reverseSearchNeedle,
                    ignoreCase = true
                )
        } ?: return

        buffer.clear()
        buffer.append(match)
        cursor = buffer.length
        activeHistoryLane = lane
        historyIndex = -1
    }

    private fun currentHistoryLane(): HistoryLane =
        if (buffer.isEmpty()) {
            lastCommittedLane
        } else {
            classify(buffer.toString())
        }

    private fun classify(
        value: String
    ): HistoryLane {
        val trimmed = value.trimStart()

        return when {
            trimmed.startsWith("!") ->
                HistoryLane.SHELL

            trimmed.startsWith("/shell") ->
                HistoryLane.SHELL

            trimmed.startsWith("/pwd") ->
                HistoryLane.SHELL

            trimmed.startsWith("/cd") ->
                HistoryLane.SHELL

            trimmed.startsWith("/ls") ->
                HistoryLane.SHELL

            trimmed.startsWith("/git") ->
                HistoryLane.SHELL

            trimmed.startsWith("/") ->
                HistoryLane.SLASH

            else ->
                HistoryLane.PROMPT
        }
    }

    private fun isSlashSuggestionActive(): Boolean {
        if (slashDismissed) return false

        val beforeCursor = buffer.substring(
            0,
            cursor.coerceIn(0, buffer.length)
        ).trimStart()

        return beforeCursor.startsWith("/") &&
            beforeCursor.none(Char::isWhitespace) &&
            !beforeCursor.contains('\n')
    }
}
