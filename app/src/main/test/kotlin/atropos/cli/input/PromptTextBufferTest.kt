/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.input

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PromptTextBufferTest {

    @Test
    fun `insert advances the cursor past the inserted text`() {
        val buffer = PromptTextBuffer()
        assertTrue(buffer.insert("hello"))
        assertEquals("hello", buffer.text)
        assertEquals(5, buffer.cursor)
    }

    @Test
    fun `insert lands at the cursor rather than the end`() {
        val buffer = PromptTextBuffer()
        buffer.insert("hed")
        buffer.moveLeft()
        buffer.insert("llo worl")
        assertEquals("hello world", buffer.text)
    }

    @Test
    fun `insert past the cap is refused whole rather than truncated`() {
        val buffer = PromptTextBuffer(maximumLength = 8)
        assertTrue(buffer.insert("1234"))
        assertFalse(buffer.insert("123456789"))
        assertEquals("1234", buffer.text, "a refused insert must leave the buffer untouched")
    }

    @Test
    fun `backspace crosses an astral codepoint whole`() {
        val buffer = PromptTextBuffer()
        buffer.insert("a🚀")
        assertEquals(3, buffer.length, "rocket occupies two UTF-16 units")
        assertTrue(buffer.backspace())
        assertEquals("a", buffer.text, "a surrogate pair must not be split")
        assertEquals(1, buffer.cursor)
    }

    @Test
    fun `delete removes the codepoint at the cursor whole`() {
        val buffer = PromptTextBuffer()
        buffer.insert("🚀z")
        buffer.moveHome()
        assertTrue(buffer.delete())
        assertEquals("z", buffer.text)
    }

    @Test
    fun `movement steps by codepoint in both directions`() {
        val buffer = PromptTextBuffer()
        buffer.insert("🚀")
        assertTrue(buffer.moveLeft())
        assertEquals(0, buffer.cursor)
        assertTrue(buffer.moveRight())
        assertEquals(2, buffer.cursor)
    }

    @Test
    fun `edge operations report that they did nothing`() {
        val buffer = PromptTextBuffer()
        assertFalse(buffer.backspace())
        assertFalse(buffer.delete())
        assertFalse(buffer.moveLeft())
        assertFalse(buffer.moveRight())
    }

    @Test
    fun `replace parks the cursor at the end`() {
        val buffer = PromptTextBuffer()
        buffer.insert("short")
        buffer.moveHome()
        buffer.replace("a much longer line")
        assertEquals("a much longer line", buffer.text)
        assertEquals(buffer.length, buffer.cursor)
    }

    @Test
    fun `text before cursor excludes what follows it`() {
        val buffer = PromptTextBuffer()
        buffer.insert("/status now")
        buffer.moveHome()
        buffer.moveRight()
        buffer.moveRight()
        assertEquals("/s", buffer.textBeforeCursor())
    }

    @Test
    fun `clear empties the line and resets the cursor`() {
        val buffer = PromptTextBuffer()
        buffer.insert("something")
        buffer.clear()
        assertTrue(buffer.isEmpty())
        assertEquals(0, buffer.cursor)
    }
}
