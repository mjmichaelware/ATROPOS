package atropos.cli.input

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CommandHistoryStoreTest {
    @Test
    fun reloads_bounded_redacted_history_across_store_instances() {
        val root = Files.createTempDirectory("atropos-command-history-")
        val path = root.resolve("history.tsv")
        val first = CommandHistoryStore(path, limit = 2)

        first.record(PromptHistoryLane.SLASH, "/status")
        first.record(PromptHistoryLane.SLASH, "/keys setup api_key=sk-test-secret-value")
        first.record(PromptHistoryLane.SLASH, "/providers")

        val persisted = Files.readString(path)
        val second = CommandHistoryStore(path, limit = 2)

        assertEquals("/providers", second.recall(PromptHistoryLane.SLASH, 0))
        assertTrue(second.recall(PromptHistoryLane.SLASH, 1)?.contains("<redacted:") == true)
        assertFalse("sk-test-secret-value" in persisted)
        assertTrue(persisted.isNotBlank())
    }

    @Test
    fun prompt_state_can_use_the_durable_history_owner() {
        val root = Files.createTempDirectory("atropos-command-history-state-")
        val path = root.resolve("nested/history.tsv")
        val first = PromptState(historyStore = CommandHistoryStore(path))

        "/status".forEach { first.apply(KeyEvent.Printable(it.toString())) }
        first.apply(KeyEvent.Enter)

        val second = PromptState(historyStore = CommandHistoryStore(path))
        second.apply(KeyEvent.Printable("/"))
        second.historyUp()
        assertTrue(second.text == "/status")
    }

    @Test
    fun exit_commands_are_never_persisted_or_loaded_into_history() {
        val root = Files.createTempDirectory("atropos-command-history-exit-")
        val path = root.resolve("history.tsv")
        val store = CommandHistoryStore(path)

        store.record(PromptHistoryLane.SLASH, "/exit")
        store.record(PromptHistoryLane.SLASH, "/quit")
        store.record(PromptHistoryLane.PROMPT, "exit")
        store.record(PromptHistoryLane.PROMPT, "quit")

        assertTrue(store.isEmpty(PromptHistoryLane.SLASH))
        assertTrue(store.isEmpty(PromptHistoryLane.PROMPT))

        // Pre-populate with a base64-encoded exit command and verify load filters it out
        Files.writeString(path, "SLASH\t" + java.util.Base64.getEncoder().encodeToString("/exit".toByteArray()) + "\n")
        val reloaded = CommandHistoryStore(path)
        assertTrue(reloaded.isEmpty(PromptHistoryLane.SLASH))
    }

    @Test
    fun prompt_state_commit_never_records_exit_commands() {
        val root = Files.createTempDirectory("atropos-prompt-exit-")
        val path = root.resolve("history.tsv")
        val prompt = PromptState(historyStore = CommandHistoryStore(path))

        "/exit".forEach { prompt.apply(KeyEvent.Printable(it.toString())) }
        prompt.apply(KeyEvent.Enter)

        val reloaded = PromptState(historyStore = CommandHistoryStore(path))
        reloaded.historyUp()
        assertEquals("", reloaded.text, "historyUp recalled /exit instead of staying blank")
    }
}
