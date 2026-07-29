package atropos.cli.commands

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SelfHostNaturalLanguageRouterTest {
    @Test
    fun routes_plain_atropos_self_build_intent_to_phase_11_start() {
        val routed = SelfHostNaturalLanguageRouter().route(
            listOf("make", "ATROPOS", "build", "itself", "from", "inside", "out")
        )

        assertEquals(
            listOf("/agent", "self-host", "run", "make", "ATROPOS", "build", "itself", "from", "inside", "out"),
            routed
        )
    }

    @Test
    fun ignores_unrelated_agent_text() {
        assertNull(SelfHostNaturalLanguageRouter().route(listOf("/agent", "ask", "what", "is", "ATROPOS")))
        assertNull(SelfHostNaturalLanguageRouter().route(listOf("build", "a", "calculator")))
        assertNull(SelfHostNaturalLanguageRouter().route(listOf("/agent", "self-host", "start", "build", "ATROPOS")))
    }

    @Test
    fun routes_plain_atropos_recovery_intent_to_recover() {
        val routed = SelfHostNaturalLanguageRouter().route(
            listOf("continue", "ATROPOS", "self-host", "after", "restart")
        )

        assertEquals(
            listOf("/agent", "self-host", "recover"),
            routed
        )
    }
}
