package atropos.core.agent

import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AgentRepairBudgetTest {
    @Test
    fun budget_allows_bounded_retries_and_stops_at_attempt_limit() {
        val started = Instant.parse("2026-08-20T00:00:00Z")
        val budget = AgentRepairBudget(maxAttempts = 3, maxDuration = Duration.ofMinutes(10))

        assertTrue(budget.allows(0, started, started.plusSeconds(1)))
        assertTrue(budget.allows(2, started, started.plusSeconds(1)))
        assertFalse(budget.allows(3, started, started.plusSeconds(1)))
        assertFalse(budget.allows(0, started, started.plusSeconds(601)))
    }
}
