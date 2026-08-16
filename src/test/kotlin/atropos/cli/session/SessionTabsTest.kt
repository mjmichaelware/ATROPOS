package atropos.cli.session

import kotlin.test.Test
import kotlin.test.assertEquals

class SessionTabsTest {
    @Test
    fun `snapshot publishes active tab state to restoration owner`() {
        val project = "session-test-${System.nanoTime()}"
        val first = SessionTabs("groq", ".", project)
        first.renameTab(1, "Work")
        first.snapshot()

        val reopened = SessionTabs("groq", ".", project)
        assertEquals("Work", reopened.active.title)
    }
}
