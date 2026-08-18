/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.agent

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SelfHostRuntimeRunLimitsTest {

    /**
     * Contract change, deliberate.
     *
     * These assertions used to pin a flat default of 25 and a ceiling of 100.
     * The budget is spent per DAG node, so a flat default is either wasteful on
     * a three-node graph or -- the case that mattered -- ends a four-hundred
     * node run at six percent of the work, reporting a normal exit.
     */
    @Test
    fun budget_follows_the_size_of_the_graph() {
        assertEquals(15, SelfHostRuntimeRunLimits.forNodeCount(3, emptyMap()) { null })
        assertEquals(1206, SelfHostRuntimeRunLimits.forNodeCount(400, emptyMap()) { null })
    }

    @Test
    fun an_unknown_graph_falls_back_rather_than_ending_the_run() {
        // Zero means "the DAG is not built yet", not "there is nothing to do".
        // Deriving from it would hand the run six advances and call that a
        // budget.
        assertEquals(
            SelfHostRuntimeRunLimits.FALLBACK_MAX_ADVANCES,
            SelfHostRuntimeRunLimits.forNodeCount(0, emptyMap()) { null }
        )
        assertEquals(
            SelfHostRuntimeRunLimits.FALLBACK_MAX_ADVANCES,
            SelfHostRuntimeRunLimits.forNodeCount(-4, emptyMap()) { null }
        )
    }

    @Test
    fun an_operator_who_names_a_number_gets_it() {
        val environment = mapOf("ATROPOS_SELF_HOST_MAX_ADVANCES" to "7")

        assertEquals(7, SelfHostRuntimeRunLimits.forNodeCount(400, environment) { null })
        assertEquals(7, SelfHostRuntimeRunLimits.override(environment) { null })
    }

    @Test
    fun a_system_property_works_when_the_environment_is_not_writable() {
        assertEquals(9, SelfHostRuntimeRunLimits.forNodeCount(3, emptyMap()) { "9" })
    }

    @Test
    fun no_override_is_reported_as_none_rather_than_as_a_number() {
        assertNull(SelfHostRuntimeRunLimits.override(emptyMap()) { null })
        assertNull(SelfHostRuntimeRunLimits.override(mapOf("ATROPOS_SELF_HOST_MAX_ADVANCES" to "nonsense")) { null })
    }

    @Test
    fun the_budget_stays_bounded_at_both_ends() {
        assertEquals(1, SelfHostRuntimeRunLimits.forNodeCount(3, mapOf("ATROPOS_SELF_HOST_MAX_ADVANCES" to "0")) { null })
        assertEquals(
            SelfHostRuntimeRunLimits.MAX_ALLOWED_ADVANCES,
            SelfHostRuntimeRunLimits.forNodeCount(3, emptyMap()) { "99999" }
        )
        // An autonomous loop on someone's phone is bounded even when the graph
        // says otherwise.
        assertEquals(
            SelfHostRuntimeRunLimits.MAX_ALLOWED_ADVANCES,
            SelfHostRuntimeRunLimits.forNodeCount(100_000, emptyMap()) { null }
        )
    }
}
