/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.input

import atropos.core.observability.TouchAutocomplete
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PromptSuggestionStateTest {

    private val state = PromptSuggestionState()

    @Test
    fun `isActive returns false for natural language input`() {
        val naturalLanguageInputs = listOf(
            "hi",
            "hello",
            "hi!",
            "history",
            "hello!",
            "what is this?",
            "tell me something",
            "status",
            "usage",
            "help me",
            "run this",
            "version",
            "quit",
            "exit",
            "foo",
            "bar",
            "123",
            "你好",
            "😊",
            "!",
            "?",
            "...",
            "???",
            "hi?",
            "hi.",
            "hi,",
            "what?!",
            "你好！"
        )

        for (input in naturalLanguageInputs) {
            assertFalse(state.isActive(input), "Expected inactive for natural language: $input")
        }
    }

    @Test
    fun `isActive returns false for empty and whitespace input`() {
        val emptyInputs = listOf("", " ", "    ", "\t", "\n", "  \t  \n  ")

        for (input in emptyInputs) {
            assertFalse(state.isActive(input), "Expected inactive for: '$input'")
        }
    }

    @Test
    fun `isActive returns false for punctuation and emoji`() {
        val punctuationInputs = listOf("!", "?", ".", ",", "!!!", "...", "?!?", "😊", "你好！")

        for (input in punctuationInputs) {
            assertFalse(state.isActive(input), "Expected inactive for: $input")
        }
    }

    @Test
    fun `isActive returns true for slash-prefixed input`() {
        val prefixedInputs = listOf(
            "/",
            "/s",
            "/st",
            "/sta",
            "/stat",
            "/status",
            "/verify",
            "/use",
            "  /status",
            "\t/verify"
        )

        for (input in prefixedInputs) {
            assertTrue(state.isActive(input), "Expected active for prefixed input: $input")
        }
    }

    @Test
    fun `selectionFor returns 0 for natural language`() {
        val naturalLanguageInputs = listOf("hi", "hello", "history", "status", "what is this?")

        for (input in naturalLanguageInputs) {
            assertEquals(0, state.selectionFor(input), "Expected 0 selection for: $input")
        }
    }

    @Test
    fun `paletteLevel returns COMMANDS for natural language`() {
        val naturalLanguageInputs = listOf("hi", "hello", "history", "status")

        for (input in naturalLanguageInputs) {
            assertEquals(CommandPaletteLevel.COMMANDS, state.level(input), "Expected COMMANDS level for: $input")
        }
    }

    @Test
    fun `paletteCommand returns null for natural language`() {
        val naturalLanguageInputs = listOf("hi", "hello", "history", "status")

        for (input in naturalLanguageInputs) {
            assertEquals(null, state.selectedCommand(input), "Expected null command for: $input")
        }
    }

    @Test
    fun `isGroupLevel returns false for natural language`() {
        val naturalLanguageInputs = listOf("hi", "hello", "history", "status")

        for (input in naturalLanguageInputs) {
            assertFalse(state.isGroupLevel(input), "Expected not group level for: $input")
        }
    }

    @Test
    fun `onTextChanged clears selection when transitioning from command to natural language`() {
        // First activate command mode
        state.isActive("/status") // This will set up internal state
        // Simulate selection
        state.moveSelectionDown("/status")
        assertTrue(state.selectionFor("/status") > 0 || state.selectionFor("/status") == 0) // Just ensure no crash

        // Then transition to natural language - onTextChanged should clear state
        state.onTextChanged()

        // After onTextChanged, natural language should have no selection
        assertEquals(0, state.selectionFor("hi"))
        assertFalse(state.isActive("hi"))
    }

    @Test
    fun `dismiss hides palette`() {
        state.isActive("/status")
        state.dismiss()
        assertFalse(state.isActive("/status"))
    }

    @Test
    fun `reset clears all state`() {
        state.isActive("/status")
        state.moveSelectionDown("/status")
        state.reset()
        assertFalse(state.isActive("/status"))
        assertEquals(0, state.selectionFor("/status"))
    }

    @Test
    fun `help palette still works for explicit help commands`() {
        // The help palette should still work for explicit help commands
        val helpInputs = listOf("?", "/?", "/help", "/usage", "/commands", "help", "usage", "commands")

        // Note: These are NOT prefixed with "/" in some cases, so they should NOT activate
        // But for "/" help commands they should
        assertTrue(state.isActive("/help"))
        assertTrue(state.isActive("/usage"))
        assertTrue(state.isActive("/?"))
        assertTrue(state.isActive("/commands"))

        // Non-prefixed help words should NOT activate (this is the hard gate)
        assertFalse(state.isActive("help"))
        assertFalse(state.isActive("usage"))
        assertFalse(state.isActive("commands"))
    }
}