package atropos.core.agent

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AgentFailureSummaryTest {

    private val summary = AgentFailureSummary()

    @Test
    fun `a message is trimmed and kept`() {
        assertEquals("connection reset", summary.compact("  connection reset  "))
    }

    @Test
    fun `a null message becomes a fixed line rather than empty`() {
        assertEquals(AgentFailureSummary.CASCADE_FAILED, summary.compact(null))
    }

    @Test
    fun `a blank message becomes a fixed line`() {
        assertEquals(AgentFailureSummary.CASCADE_FAILED, summary.compact("   "))
        assertEquals(
            AgentFailureSummary.CASCADE_FAILED,
            summary.compact(""),
            "a failure must never be recorded as though nothing went wrong"
        )
    }

    @Test
    fun `a stack-trace-length message is bounded`() {
        val long = "at atropos.core.Provider.call(Provider.kt:42)\n".repeat(200)
        assertTrue(
            summary.compact(long).length <= AgentFailureSummary.MAXIMUM_CHARACTERS,
            "an unbounded failure line makes the durable record the biggest thing in the repo"
        )
    }
}
