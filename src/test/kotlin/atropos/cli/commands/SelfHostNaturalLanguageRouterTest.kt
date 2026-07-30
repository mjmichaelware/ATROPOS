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

    @Test
    fun routes_operator_self_improvement_phrases_to_phase_11_run() {
        val router = SelfHostNaturalLanguageRouter()

        assertEquals(
            listOf("/agent", "self-host", "run", "ATROPOS,", "improve", "yourself"),
            router.route(listOf("ATROPOS,", "improve", "yourself"))
        )
        assertEquals(
            listOf("/agent", "self-host", "run", "ATROPOS", "run", "self-host", "Phase", "11"),
            router.route(listOf("ATROPOS", "run", "self-host", "Phase", "11"))
        )
        assertEquals(
            listOf("/agent", "self-host", "run", "build", "yourself"),
            router.route(listOf("build", "yourself"))
        )
        assertEquals(
            listOf("/agent", "self-host", "run", "run", "self-host", "Phase", "11"),
            router.route(listOf("run", "self-host", "Phase", "11"))
        )
    }
}
