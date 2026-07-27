package atropos.cli.input

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PromptStateTest {
    @Test
    fun committed_history_is_redacted_before_recall() {
        val state = PromptState()
        state.insert("api_key=sk-ABCDEFGHIJKLMNOPQRSTUVWX /tmp/client_secret-prod.json")

        val committed = state.commit()
        assertTrue(committed.contains("sk-ABCDEFGHIJKLMNOPQRSTUVWX"))

        state.historyUp()

        assertTrue(state.text.contains("<redacted"))
        assertFalse(state.text.contains("sk-ABCDEFGHIJKLMNOPQRSTUVWX"))
        assertFalse(state.text.contains("client_secret-prod.json"))
    }
}
