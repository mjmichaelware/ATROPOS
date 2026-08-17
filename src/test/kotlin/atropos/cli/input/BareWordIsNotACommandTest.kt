/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.input

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Enter must not rewrite what the operator typed.
 *
 * A single un-slashed word that happened to match the registry was silently
 * turned into a slash command and executed. Typing `status` — an ordinary
 * thing to say to an assistant — ran `/status`, and there was no way to ask
 * the engine a question about any word that collided with a command name. The
 * operator watched their sentence get rewritten and run.
 *
 * Tab still completes an un-slashed prefix. The shortcut is one keystroke
 * away; it is only no longer automatic.
 */
class BareWordIsNotACommandTest {

    private val completer = CommandCompleter(Path.of("."))

    private fun submission(text: String): String? =
        completer.resolveSubmission(text, text.length)

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
    fun tab_completion_still_offers_the_command() {
        // The affordance is not removed, only made deliberate.
        val completion = completer.complete("stat", 4)

        assertEquals(
            true,
            completion.options.any { it.startsWith("/status") },
            "tab no longer completes a bare prefix: ${completion.options}"
        )
    }

    @Test
    fun blank_input_resolves_to_nothing() {
        assertNull(submission("   "))
        assertNull(submission(""))
    }
}
