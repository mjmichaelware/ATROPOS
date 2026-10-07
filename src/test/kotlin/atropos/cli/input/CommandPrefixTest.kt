/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.input

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CommandPrefixTest {

    @Test
    fun `isCommandPrefixed returns true for slash-prefixed input`() {
        assertTrue(CommandPrefix.isCommandPrefixed("/"))
        assertTrue(CommandPrefix.isCommandPrefixed("/status"))
        assertTrue(CommandPrefix.isCommandPrefixed("  /status"))
        assertTrue(CommandPrefix.isCommandPrefixed("\t/verify narrow"))
        assertTrue(CommandPrefix.isCommandPrefixed("/use groq"))
    }

    @Test
    fun `isCommandPrefixed returns false for non-prefixed input`() {
        assertFalse(CommandPrefix.isCommandPrefixed("hi"))
        assertFalse(CommandPrefix.isCommandPrefixed("hello"))
        assertFalse(CommandPrefix.isCommandPrefixed("hi!"))
        assertFalse(CommandPrefix.isCommandPrefixed("history"))
        assertFalse(CommandPrefix.isCommandPrefixed("hello!"))
        assertFalse(CommandPrefix.isCommandPrefixed("what is this?"))
        assertFalse(CommandPrefix.isCommandPrefixed("tell me something"))
        assertFalse(CommandPrefix.isCommandPrefixed("status"))
        assertFalse(CommandPrefix.isCommandPrefixed("usage"))
        assertFalse(CommandPrefix.isCommandPrefixed("help me"))
        assertFalse(CommandPrefix.isCommandPrefixed("run this"))
        assertFalse(CommandPrefix.isCommandPrefixed("version"))
        assertFalse(CommandPrefix.isCommandPrefixed("quit"))
        assertFalse(CommandPrefix.isCommandPrefixed("exit"))
        assertFalse(CommandPrefix.isCommandPrefixed("foo"))
        assertFalse(CommandPrefix.isCommandPrefixed("bar"))
        assertFalse(CommandPrefix.isCommandPrefixed("123"))
        assertFalse(CommandPrefix.isCommandPrefixed("你好"))
        assertFalse(CommandPrefix.isCommandPrefixed("😊"))
    }

    @Test
    fun `isCommandPrefixed returns false for empty and whitespace-only input`() {
        assertFalse(CommandPrefix.isCommandPrefixed(""))
        assertFalse(CommandPrefix.isCommandPrefixed(" "))
        assertFalse(CommandPrefix.isCommandPrefixed("    "))
        assertFalse(CommandPrefix.isCommandPrefixed("\t"))
        assertFalse(CommandPrefix.isCommandPrefixed("\n"))
        assertFalse(CommandPrefix.isCommandPrefixed("  \t  \n  "))
    }

    @Test
    fun `isCommandPrefixed returns false for punctuation and emoji`() {
        assertFalse(CommandPrefix.isCommandPrefixed("!"))
        assertFalse(CommandPrefix.isCommandPrefixed("?"))
        assertFalse(CommandPrefix.isCommandPrefixed("."))
        assertFalse(CommandPrefix.isCommandPrefixed(","))
        assertFalse(CommandPrefix.isCommandPrefixed("!!!"))
        assertFalse(CommandPrefix.isCommandPrefixed("..."))
        assertFalse(CommandPrefix.isCommandPrefixed("?!?"))
        assertFalse(CommandPrefix.isCommandPrefixed("😊"))
        assertFalse(CommandPrefix.isCommandPrefixed("你好！"))
    }

    @Test
    fun `classify returns Empty for empty input`() {
        assertEquals(CommandPrefix.InputClassification.Empty, CommandPrefix.classify(""))
        assertEquals(CommandPrefix.InputClassification.Empty, CommandPrefix.classify(" "))
        assertEquals(CommandPrefix.InputClassification.Empty, CommandPrefix.classify("\t"))
        assertEquals(CommandPrefix.InputClassification.Empty, CommandPrefix.classify("\n"))
    }

    @Test
    fun `classify returns Command for prefixed input`() {
        val result = CommandPrefix.classify("/status")
        assertTrue(result is CommandPrefix.InputClassification.Command)
        assertEquals("/status", (result as CommandPrefix.InputClassification.Command).input)

        val result2 = CommandPrefix.classify("  /verify narrow")
        assertTrue(result2 is CommandPrefix.InputClassification.Command)
        assertEquals("  /verify narrow", (result2 as CommandPrefix.InputClassification.Command).input)
    }

    @Test
    fun `classify returns NaturalLanguage for non-prefixed input`() {
        val result = CommandPrefix.classify("hi")
        assertTrue(result is CommandPrefix.InputClassification.NaturalLanguage)
        assertEquals("hi", (result as CommandPrefix.InputClassification.NaturalLanguage).input)

        val result2 = CommandPrefix.classify("history")
        assertTrue(result2 is CommandPrefix.InputClassification.NaturalLanguage)
        assertEquals("history", (result2 as CommandPrefix.InputClassification.NaturalLanguage).input)

        val result3 = CommandPrefix.classify("what is this?")
        assertTrue(result3 is CommandPrefix.InputClassification.NaturalLanguage)
    }

    @Test
    fun `firstNonWhitespace returns first non-whitespace character`() {
        assertEquals('/', CommandPrefix.firstNonWhitespace("/status"))
        assertEquals('/', CommandPrefix.firstNonWhitespace("  /status"))
        assertEquals('h', CommandPrefix.firstNonWhitespace("hi"))
        assertEquals('h', CommandPrefix.firstNonWhitespace("  hi"))
        assertEquals('😊', CommandPrefix.firstNonWhitespace("  😊"))
        assertEquals(null, CommandPrefix.firstNonWhitespace(""))
        assertEquals(null, CommandPrefix.firstNonWhitespace("   "))
    }
}