package atropos.cli.input

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import java.nio.file.Path

class CommandCompleterTest {
    /**
     * Deliberate contract change: an un-slashed word is prose.
     *
     * This test previously asserted that `help` and `usage` resolved to
     * `/help` on Enter. They no longer do, and that is the fix rather than a
     * regression — a bare word matching the registry was being silently
     * rewritten and executed, so there was no way to ask the engine about any
     * word that collided with a command name. Tab still completes them.
     *
     * `self-host` keeps resolving: it is an explicit multi-word alias the
     * operator chose, not a collision.
     */
    @Test
    fun resolveSubmission_requires_a_slash_except_for_the_self_host_alias() {
        val completer = CommandCompleter(Path.of("."))

        assertEquals(null, completer.resolveSubmission("?", 1))
        assertEquals(null, completer.resolveSubmission("usage", 5))
        assertEquals(null, completer.resolveSubmission("help", 4))

        assertEquals("/help", completer.resolveSubmission("/usage", 7))
        assertEquals("/self-host", completer.resolveSubmission("self-host", 9))
        assertEquals("/self-host build yourself", completer.resolveSubmission("self-host build yourself", 24))
        assertEquals("/self-host run", completer.resolveSubmission("self-host run", 13))
    }

    @Test
    fun complete_prefers_canonical_commands_for_alias_prefixes() {
        val completer = CommandCompleter(Path.of("."))

        val helpCompletion = completer.complete("usage", 5)
        assertEquals("/help", helpCompletion.options.first())
        assertEquals("", helpCompletion.insertion)
        assertEquals("/help", helpCompletion.preview)

        val selfHostCompletion = completer.complete("self-host", 9)
        assertEquals("/self-host", selfHostCompletion.options.first())
        assertEquals("", selfHostCompletion.insertion)
        assertEquals("/self-host", selfHostCompletion.preview)
    }

    @Test
    fun complete_supports_short_prefixes_and_enter_resolution() {
        val completer = CommandCompleter(Path.of("."))

        val completion = completer.complete("/quo", 4)

        assertTrue(completion.options.isNotEmpty(), completion.options.joinToString(", "))
        assertTrue(completion.options.first().startsWith("/status"), completion.options.joinToString(", "))
        assertEquals("", completion.insertion)
        assertEquals("/status quota", completer.resolveSubmission("/quo", 4))
    }

    /**
     * The selection is still honoured — but only once a slash has declared
     * that a command was meant. `status` alone is now a word.
     */
    @Test
    fun enter_preserves_selected_command_for_slashed_prefixes() {
        val completer = CommandCompleter(Path.of("."))

        assertEquals(null, completer.resolveSubmission("status", 6, 3))

        // selectedIndex 3 picks the status-adapters entry from search results
        val statusResult = completer.resolveSubmission("/status", 7, 3)
        assertTrue(statusResult != null && statusResult.contains("status"), "slashed prefix should resolve: $statusResult")
        // selectedIndex 2 picks a self-host variant from search results
        val selfHostResult = completer.resolveSubmission("self-host", 9, 2)
        assertTrue(selfHostResult != null && selfHostResult.contains("self-host"), "self-host prefix should resolve: $selfHostResult")
    }

    @Test
    fun enter_does_not_rewrite_plain_natural_language_into_a_command() {
        val completer = CommandCompleter(Path.of("."))
        val prompt = "build a simple calculator CLI with tests and README"
        assertEquals(null, completer.resolveSubmission(prompt, prompt.length))
    }

    @Test
    fun risky_partial_rewrite_is_marked_for_confirmation() {
        val completer = CommandCompleter(Path.of("."))

        val resolved = completer.resolveSubmission("/she", 4)

        assertEquals("/shell", resolved)
        assertTrue(completer.lastResolutionWasFuzzy)
    }

    @Test
    fun complete_replaces_bare_command_prefixes_with_canonical_commands() {
        val completer = CommandCompleter(Path.of("."))

        val completion = completer.complete("help", 4)

        assertTrue(completion.options.isNotEmpty(), completion.options.joinToString(", "))
        assertEquals("", completion.insertion)
        assertEquals("/help", completion.preview)
    }
}
