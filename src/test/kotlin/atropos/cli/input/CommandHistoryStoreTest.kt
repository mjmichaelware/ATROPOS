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
}
