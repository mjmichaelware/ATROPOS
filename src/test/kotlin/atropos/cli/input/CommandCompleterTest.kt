/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.input

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CommandCompleterTest {

    private val completer = CommandCompleter(Path.of("."))

    @Test
    fun `complete returns empty for natural language input`() {
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
            val completion = completer.complete(input, input.length)
            assertTrue(completion.options.isEmpty(), "Expected empty completion for: $input")
            assertEquals("", completion.insertion, "Expected empty insertion for: $input")
            assertEquals("", completion.preview, "Expected empty preview for: $input")
        }
    }

    @Test
    fun `complete returns empty for empty and whitespace input`() {
        val emptyInputs = listOf("", " ", "    ", "\t", "\n", "  \t  \n  ")

        for (input in emptyInputs) {
            val completion = completer.complete(input, input.length)
            assertTrue(completion.options.isEmpty(), "Expected empty completion for: '$input'")
        }
    }

    @Test
    fun `complete returns empty for punctuation and emoji`() {
        val punctuationInputs = listOf("!", "?", ".", ",", "!!!", "...", "?!?", "😊", "你好！")

        for (input in punctuationInputs) {
            val completion = completer.complete(input, input.length)
            assertTrue(completion.options.isEmpty(), "Expected empty completion for: $input")
        }
    }

    @Test
    fun `complete returns candidates for slash-prefixed input`() {
        // These should produce completion candidates (exact commands may vary)
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
            val completion = completer.complete(input, input.length)
            // Should produce some candidates for valid command prefixes
            // Note: exact candidates depend on registered commands
            assertTrue(completion.options.isNotEmpty() || completion.insertion.isNotEmpty(),
                "Expected candidates for prefixed input: $input")
        }
    }

    @Test
    fun `resolveSubmission returns null for natural language`() {
        val naturalLanguageInputs = listOf(
            "hi",
            "hello",
            "history",
            "what is this?",
            "status",
            "usage",
            "help me",
            "version",
            "quit",
            "exit"
        )

        for (input in naturalLanguageInputs) {
            val result = completer.resolveSubmission(input, input.length)
            assertEquals(null, result, "Expected null for natural language: $input")
        }
    }

    @Test
    fun `resolveSubmission returns null for empty input`() {
        val emptyInputs = listOf("", " ", "    ", "\t", "\n")

        for (input in emptyInputs) {
            val result = completer.resolveSubmission(input, input.length)
            assertEquals(null, result, "Expected null for empty input: '$input'")
        }
    }

    @Test
    fun `resolveSubmission returns command for slash-prefixed input`() {
        val result = completer.resolveSubmission("/status", "/status".length)
        assertTrue(result != null && result.startsWith("/status"),
            "Expected command resolution for /status, got: $result")
    }

    @Test
    fun `completion state clears when transitioning from command to natural language`() {
        // First, enter command mode
        var completion = completer.complete("/hist", 5)
        assertTrue(completion.options.isNotEmpty(), "Should have candidates for /hist")

        // Then transition to natural language
        completion = completer.complete("hi", 2)
        assertTrue(completion.options.isEmpty(), "Should have NO candidates for 'hi' after command mode")

        // Enter should not resurrect stale state
        val result = completer.resolveSubmission("hi", 2)
        assertEquals(null, result, "Enter on 'hi' should return null (natural language)")
    }

    @Test
    fun `verify subcommand completion works`() {
        val completion = completer.complete("/verify n", 8)
        // Should offer "narrow" and "wide"
        assertTrue(completion.options.contains("narrow") || completion.options.contains("wide"),
            "Expected narrow/wide for /verify")
    }

    @Test
    fun `use subcommand completion works`() {
        val completion = completer.complete("/use g", 6)
        // Should offer provider completions (fuzzy)
        assertTrue(completion.options.isNotEmpty(), "Expected provider completions for /use")
    }

    @Test
    fun `fuzzy completion works inside command mode`() {
        // "/sta" should complete to "/status" via fuzzy matching
        val completion = completer.complete("/sta", 4)
        assertTrue(completion.options.contains("/status") || completion.insertion.contains("tus"),
            "Expected /status completion for /sta, got: ${completion.options}, insertion: ${completion.insertion}")
    }
}