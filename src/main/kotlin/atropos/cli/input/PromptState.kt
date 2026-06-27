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

class PromptState(
    private val historyLimit: Int = 100,
    private val maximumBufferLength: Int =
        1024 * 1024
) {
    private val buffer = StringBuilder()
    private val history =
        mutableListOf<String>()

    private var historyIndex = -1
    private var savedBuffer = ""

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

    fun apply(event: KeyEvent): PromptEffect {
        return when (event) {
            is KeyEvent.Printable ->
                insertEvent(event.text)

            is KeyEvent.Paste ->
                insertEvent(event.text)

            is KeyEvent.InvalidInput ->
                PromptEffect.InputError(
                    event.reason
                )

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
                historyUp()
                PromptEffect.Redraw
            }

            KeyEvent.ArrowDown -> {
                historyDown()
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

            KeyEvent.Escape ->
                PromptEffect.None
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
        if (history.isEmpty()) return

        if (historyIndex == -1) {
            savedBuffer = buffer.toString()
        }

        if (historyIndex <
            history.lastIndex
        ) {
            historyIndex++
        }

        loadHistory()
    }

    fun historyDown() {
        when {
            historyIndex > 0 -> {
                historyIndex--
                loadHistory()
            }

            historyIndex == 0 -> {
                historyIndex = -1
                buffer.clear()
                buffer.append(savedBuffer)
                cursor = buffer.length
            }
        }
    }

    fun commit(): String {
        val result = buffer.toString()

        if (result.isNotBlank() &&
            history.lastOrNull() != result
        ) {
            history += result

            while (history.size >
                historyLimit
            ) {
                history.removeAt(0)
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

    private fun loadHistory() {
        val sourceIndex =
            history.lastIndex -
                historyIndex

        buffer.clear()
        buffer.append(history[sourceIndex])
        cursor = buffer.length
    }

    private fun detachHistory() {
        if (historyIndex == -1) return

        historyIndex = -1
        savedBuffer = ""
    }
}
