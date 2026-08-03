/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.input

/**
 * A bounded, codepoint-safe editable line with a cursor.
 *
 * Extracted from the prompt state machine because editing is mechanical and
 * total — every operation here is decidable from the buffer and the cursor
 * alone, with no reference to key events, history, or command suggestions.
 * Keeping it separate means the text-manipulation rules can be tested against
 * the awkward inputs that actually break them (astral-plane characters, empty
 * buffers, cursor at either edge) without constructing a key stream.
 *
 * ## Codepoints, not chars
 *
 * Kotlin's `String` is UTF-16, so an emoji or any astral-plane character
 * occupies two `Char` slots. Moving or deleting by one `Char` would land the
 * cursor between the halves of a surrogate pair and split it, producing a
 * corrupted line that renders as a replacement glyph. Every movement and
 * deletion here steps by codepoint so a character is always crossed whole.
 *
 * ## The length cap is a refusal, not a truncation
 *
 * [insert] returns false rather than inserting a prefix when the result would
 * exceed the cap. Silently keeping part of a paste would leave the operator
 * with a line that looks complete and is not.
 */
class PromptTextBuffer(private val maximumLength: Int = DEFAULT_MAXIMUM_LENGTH) {

    init {
        require(maximumLength > 0) { "maximum buffer length must be positive" }
    }

    private val buffer = StringBuilder()

    var cursor: Int = 0
        private set

    val text: String get() = buffer.toString()

    val length: Int get() = buffer.length

    fun isEmpty(): Boolean = buffer.isEmpty()

    fun isNotEmpty(): Boolean = buffer.isNotEmpty()

    /**
     * Inserts [value] at the cursor.
     *
     * @return false when the insert would exceed the cap; the buffer is unchanged.
     */
    fun insert(value: String): Boolean {
        if (value.isEmpty()) return true
        if (buffer.length + value.length > maximumLength) return false
        buffer.insert(cursor, value)
        cursor += value.length
        return true
    }

    /** Deletes the codepoint before the cursor. @return true when something was removed. */
    fun backspace(): Boolean {
        if (cursor <= 0) return false
        val previous = Character.offsetByCodePoints(buffer, cursor, -1)
        buffer.delete(previous, cursor)
        cursor = previous
        return true
    }

    /** Deletes the codepoint at the cursor. @return true when something was removed. */
    fun delete(): Boolean {
        if (cursor >= buffer.length) return false
        val next = Character.offsetByCodePoints(buffer, cursor, 1)
        buffer.delete(cursor, next)
        return true
    }

    fun moveLeft(): Boolean {
        if (cursor <= 0) return false
        cursor = Character.offsetByCodePoints(buffer, cursor, -1)
        return true
    }

    fun moveRight(): Boolean {
        if (cursor >= buffer.length) return false
        cursor = Character.offsetByCodePoints(buffer, cursor, 1)
        return true
    }

    fun moveHome() {
        cursor = 0
    }

    fun moveEnd() {
        cursor = buffer.length
    }

    /** Replaces the whole line and parks the cursor at its end, as recall does. */
    fun replace(value: String) {
        buffer.clear()
        buffer.append(value)
        cursor = buffer.length
    }

    fun clear() {
        buffer.clear()
        cursor = 0
    }

    /**
     * The text left of the cursor.
     *
     * Command suggestion looks only at this side: what follows the cursor is not
     * part of the token being completed.
     */
    fun textBeforeCursor(): String = buffer.substring(0, cursor.coerceIn(0, buffer.length))

    private companion object {
        const val DEFAULT_MAXIMUM_LENGTH = 1024 * 1024
    }
}
