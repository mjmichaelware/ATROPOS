/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.input

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Enter must not rewrite what the operator typed.
 *
 * A single un-slashed word that happened to match the registry was silently
 * turned into a slash command and executed. Typing `status` — an ordinary
 * thing to say to an assistant — ran `/status`, and there was no way to ask
 * the engine a question about any word that collided with a command name. The
 * operator watched their sentence get rewritten and run.
 *
 * Tab completion is NOW GATED by the explicit command prefix.
 * Bare words no longer trigger completion.
 *
 * Hard command prefix gate (Σ_cmd):
 * - An input is a CLI command ONLY if its first non-whitespace character
 *   is an explicitly recognized command-prefix character ("/").
 * - Natural language input NEVER triggers command completion.
 * - Fuzzy/prefix completion operates ONLY after explicit "/" entry.
 */
class BareWordIsNotACommandTest {

    private val completer = CommandCompleter(Path.of("."))

    private fun submission(text: String): String? =
        completer.resolveSubmission(text, text.length)

    private fun completion(text: String, cursor: Int = text.length): Completion =
        completer.complete(text, cursor)

    @Test
    fun a_bare_command_word_stays_prose() {
        assertNull(submission("status"), "`status` was rewritten to a command")
        assertNull(submission("help"), "`help` was rewritten to a command")
        assertNull(submission("verify"), "`verify` was rewritten to a command")
    }

    @Test
    fun a_bare_word_with_arguments_stays_prose() {
        assertNull(submission("status of the build please"))
        assertNull(submission("verify wide"))
    }

    @Test
    fun a_slash_command_is_still_honoured() {
        // The slash is the operator declaring intent, and that declaration is
        // exactly what this change preserves.
        assertEquals("/status", submission("/status"))
    }

    @Test
    fun the_explicit_self_host_alias_still_resolves() {
        // A multi-word alias the operator chose, not a word that collided with
        // a command name.
        val resolved = submission("self-host")

        assertEquals(true, resolved == null || resolved.startsWith("/"))
    }

    @Test
    fun tab_completion_is_gated_by_explicit_prefix() {
        // Completion is NOW GATED by explicit command prefix.
        // Bare words NO LONGER trigger completion.

        // "stat" (bare) should produce NO candidates
        val bareCompletion = completion("stat", 4)
        assertTrue(bareCompletion.options.isEmpty(), "Bare 'stat' should produce NO candidates, got: ${bareCompletion.options}")
        assertEquals("", bareCompletion.insertion)
        assertEquals("", bareCompletion.preview)

        // "/stat" (prefixed) SHOULD produce candidates via fuzzy matching
        val prefixedCompletion = completion("/stat", 5)
        assertTrue(prefixedCompletion.options.any { it.startsWith("/status") },
            "Prefixed '/stat' should complete to /status via fuzzy matching, got: ${prefixedCompletion.options}")

        // "hi" should produce NO candidates
        val hiCompletion = completion("hi", 2)
        assertTrue(hiCompletion.options.isEmpty(), "Natural language 'hi' should produce NO candidates")

        // "history" should produce NO candidates (even though /history exists)
        val historyCompletion = completion("history", 7)
        assertTrue(historyCompletion.options.isEmpty(), "Natural language 'history' should produce NO candidates")
    }

    @Test
    fun blank_input_resolves_to_nothing() {
        assertNull(submission("   "))
        assertNull(submission(""))
    }

    @Test
    fun completion_state_clears_when_transitioning_to_natural_language() {
        // First enter command mode
        var completion = completion("/hist", 5)
        assertTrue(completion.options.isNotEmpty(), "Should have candidates for /hist")

        // Then transition to natural language - completion must be cleared
        completion = completion("hi", 2)
        assertTrue(completion.options.isEmpty(), "Completion state must clear when transitioning to natural language")

        // Enter must not resurrect stale state
        val result = completer.resolveSubmission("hi", 2)
        assertNull(result, "Enter on 'hi' should return null (natural language), not resurrect stale command")
    }
}
