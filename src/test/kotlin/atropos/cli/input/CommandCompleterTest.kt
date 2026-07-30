package atropos.cli.input

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import java.nio.file.Path

class CommandCompleterTest {
    @Test
    fun resolveSubmission_maps_help_and_self_host_aliases() {
        val completer = CommandCompleter(Path.of("."))

        assertEquals("/help", completer.resolveSubmission("?", 1))
        assertEquals("/help", completer.resolveSubmission("usage", 5))
        assertEquals("/help", completer.resolveSubmission("help", 4))
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

    @Test
    fun enter_preserves_selected_command_for_bare_prefixes() {
        val completer = CommandCompleter(Path.of("."))

        // selectedIndex 3 picks the status-adapters entry from search results
        val statusResult = completer.resolveSubmission("status", 6, 3)
        assertTrue(statusResult != null && statusResult.contains("status"), "status prefix should resolve: $statusResult")
        // selectedIndex 2 picks a self-host variant from search results
        val selfHostResult = completer.resolveSubmission("self-host", 9, 2)
        assertTrue(selfHostResult != null && selfHostResult.contains("self-host"), "self-host prefix should resolve: $selfHostResult")
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
