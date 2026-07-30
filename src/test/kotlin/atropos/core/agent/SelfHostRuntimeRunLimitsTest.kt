package atropos.core.agent

import kotlin.test.Test
import kotlin.test.assertEquals

class SelfHostRuntimeRunLimitsTest {
    @Test
    fun default_is_bounded_and_operator_override_is_clamped() {
        assertEquals(25, SelfHostRuntimeRunLimits.maxAdvances(emptyMap()) { null })
        assertEquals(1, SelfHostRuntimeRunLimits.maxAdvances(mapOf("ATROPOS_SELF_HOST_MAX_ADVANCES" to "0")) { null })
        assertEquals(100, SelfHostRuntimeRunLimits.maxAdvances(emptyMap()) { "999" })
        assertEquals(7, SelfHostRuntimeRunLimits.maxAdvances(emptyMap()) { "7" })
    }
}
